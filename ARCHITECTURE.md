# MAEX 交易系统架构设计文档

> 目标读者：高级后端开发工程师
> 目标：通过本文档，完整理解一个简单交易所系统的运行机制

---

## 一、系统概览

MAEX 是一个基于 Java 17 + Maven 多模块的撮合交易系统，模拟了真实交易所的核心链路：

```
用户下单 → 撮合引擎匹配 → 行情更新 → 清算结算
```

### 技术栈

| 层面 | 技术 |
|------|------|
| 语言 | Java 17 |
| 构建 | Maven 多模块 |
| 并发 | ConcurrentHashMap / ConcurrentSkipListMap / PriorityQueue |
| 工具 | Lombok（@Getter/@Setter）|
| 日志 | SLF4J |
| 测试 | JUnit 4 |

---

## 二、模块划分

```
maex/
├── maex-common      # 公共数据模型（Order、Account）
├── maex-engine      # 撮合引擎（核心）
├── maex-market      # 行情模块（订单簿）
└── maex-clearing    # 清算结算模块
```

### 模块依赖关系

```
maex-engine   ──depends──▶  maex-common
maex-clearing ──depends──▶  maex-common
maex-market   （独立，无外部依赖）
```

---

## 三、核心数据模型

### 3.1 Order（订单）

文件：`maex-common/src/main/java/com/maex/common/model/Order.java`

```
Order
├── orderId      String        订单唯一ID
├── symbol       String        交易对，如 "BTC/USDT"
├── side         String        方向：BUY / SELL
├── price        BigDecimal    委托价格
├── quantity     BigDecimal    委托数量
└── timestamp    LocalDateTime 下单时间
```

**设计要点：**
- 使用 `BigDecimal` 而非 `double`，避免浮点精度问题（金融系统强制要求）
- 实现 `Serializable`，支持序列化传输（消息队列场景）

### 3.2 Account（账户）

文件：`maex-common/src/main/java/com/maex/common/model/Account.java`

```
Account
├── accountId         String        账户ID
├── userId            String        用户ID
├── balance           BigDecimal    总余额
├── availableBalance  BigDecimal    可用余额（可下单）
├── frozenBalance     BigDecimal    冻结余额（已下单未成交）
└── currency          String        货币类型，如 "USDT"
```

**三余额模型：**
```
总余额 = 可用余额 + 冻结余额

下单时：可用余额 → 冻结余额（资金冻结）
成交时：冻结余额 → 清算（资金划转）
撤单时：冻结余额 → 可用余额（资金解冻）
```

---

## 四、模块核心流程

### 4.1 撮合引擎（maex-engine）

文件：`maex-engine/src/main/java/com/maex/engine/matching/OrderMatchingEngine.java`

#### 数据结构

```java
// 每个交易对维护独立的买卖队列
Map<String, PriorityQueue<Order>> buyOrders   // 买单：价格降序（最高价优先）
Map<String, PriorityQueue<Order>> sellOrders  // 卖单：价格升序（最低价优先）
```

#### 撮合规则：价格优先，时间优先（FIFO）

**买单撮合流程：**

```
收到 BUY 订单
    │
    ▼
查看卖单队列队首（最低卖价）
    │
    ├─ 卖价 ≤ 买价？ ──YES──▶ 成交
    │                          ├─ 成交价 = 卖单价（maker 定价）
    │                          ├─ 成交量 = min(买单剩余量, 卖单剩余量)
    │                          ├─ 更新双方剩余数量
    │                          └─ 卖单全部成交则出队，继续循环
    │
    └─ 卖价 > 买价？ ──YES──▶ 无法成交，买单入队等待
```

**卖单撮合流程（对称）：**

```
收到 SELL 订单
    │
    ▼
查看买单队列队首（最高买价）
    │
    ├─ 买价 ≥ 卖价？ ──YES──▶ 成交
    │                          ├─ 成交价 = 买单价（maker 定价）
    │                          └─ 逻辑同上
    │
    └─ 买价 < 卖价？ ──YES──▶ 无法成交，卖单入队等待
```

#### 关键代码路径

```
processOrder(order)
    ├── side == "BUY"  → matchBuyOrder()
    └── side == "SELL" → matchSellOrder()

matchBuyOrder():
    while (sellQueue非空 && 买单有剩余量):
        sellOrder = sellQueue.peek()
        if sellOrder.price <= buyOrder.price:
            tradeQty = min(sell.qty, buy.qty)
            创建 Trade 记录（用买单ID，成交价=卖单价）
            更新双方剩余量
            卖单归零则 poll() 出队
        else: break
    买单有剩余 → offer() 入买单队列
```

---

### 4.2 行情模块（maex-market）

文件：`maex-market/src/main/java/com/maex/market/OrderBook.java`

#### 职责

维护市场深度（Order Book），对外提供实时行情快照。

#### 数据结构

```java
// 线程安全的有序 Map
NavigableMap<BigDecimal, BigDecimal> buyOrders   // price → totalQty，降序
NavigableMap<BigDecimal, BigDecimal> sellOrders  // price → totalQty，升序
```

使用 `ConcurrentSkipListMap` 的原因：
- 天然有序（跳表结构）
- 线程安全（无需额外加锁）
- 支持 `NavigableMap` 接口（范围查询）

#### 核心操作

```
updateBuyOrder(price, qty)
    qty > 0 → put(price, qty)   // 新增或更新该价位挂单量
    qty = 0 → remove(price)     // 该价位无挂单，从深度图移除

getBuyOrders(depth) → List<PriceLevel>
    // 返回前 N 档买盘，用于展示盘口
```

#### 行情快照结构

```
OrderBook{symbol}
├── buyOrders  [100.5→10, 100.0→25, 99.5→8, ...]   // 买盘，降序
├── sellOrders [101.0→15, 101.5→20, 102.0→5, ...]   // 卖盘，升序
├── lastTradePrice    最新成交价
└── lastTradeQuantity 最新成交量
```

---

### 4.3 清算模块（maex-clearing）

文件：`maex-clearing/src/main/java/com/maex/clearing/ClearingService.java`

#### 职责

撮合成交后，执行资金和资产的实际划转（结算）。

#### 账户余额存储结构

```java
// accountId → { "FUNDS"→余额, "BTC"→持仓, "ETH"→持仓, ... }
Map<String, Map<String, BigDecimal>> accountBalances
```

#### 清算流程

```
submitOrderForClearing(trade)
    │
    ├─ 幂等检查：clearedOrders.contains(orderId)？已清算则跳过
    └─ 加入 clearedOrders 集合（标记已处理）

processPendingTrades()
    │
    └─ 循环 poll pendingOrders 队列
           └─ clearOrder(trade)
                  │
                  ├─ side == "BUY"
                  │      deductFunds(amount)    // 扣资金
                  │      addAsset(symbol, qty)  // 加资产
                  │
                  └─ side == "SELL"
                         deductAsset(symbol, qty) // 扣资产
                         addFunds(amount)          // 加资金
```

#### 异常体系

```
RuntimeException
├── InsufficientFundsException   // 资金不足（买入时）
└── InsufficientAssetsException  // 资产不足（卖出时）
```

---

## 五、完整交易链路

```
┌─────────────────────────────────────────────────────────────┐
│                      完整交易链路                            │
│                                                             │
│  1. 用户下单                                                 │
│     Order(orderId, symbol, side, price, qty)                │
│              │                                              │
│              ▼                                              │
│  2. 撮合引擎（maex-engine）                                  │
│     OrderMatchingEngine.processOrder(order)                 │
│     ├─ 与对手方订单比价                                       │
│     ├─ 生成 Trade 列表（可能多笔部分成交）                     │
│     └─ 未成交部分挂入订单队列                                  │
│              │                                              │
│              ▼                                              │
│  3. 行情更新（maex-market）                                  │
│     OrderBook.updateBuyOrder / updateSellOrder              │
│     OrderBook.recordTrade(price, qty)                       │
│     └─ 更新盘口深度 + 最新成交价                              │
│              │                                              │
│              ▼                                              │
│  4. 清算结算（maex-clearing）                                │
│     ClearingService.submitOrderForClearing(trade)           │
│     ClearingService.processPendingTrades()                  │
│     └─ 资金/资产实际划转，生成清算记录                         │
└─────────────────────────────────────────────────────────────┘
```

---

## 六、测试方案

### 6.1 测试分层策略

```
单元测试（Unit Test）
    └─ 每个模块独立测试，Mock 依赖

集成测试（Integration Test）
    └─ engine + clearing 联动测试

场景测试（Scenario Test）
    └─ 模拟真实交易场景端到端验证
```

---

### 6.2 maex-engine 撮合引擎测试

**测试意图：验证撮合逻辑的正确性，覆盖所有价格匹配边界条件**

#### 测试用例

```java
// 测试1：完全成交（买卖数量相等）
// 意图：验证基础撮合路径，成交后双方队列均清空
Order sell = new Order("S1", "BTC/USDT", "SELL", new BigDecimal("100"), new BigDecimal("10"));
Order buy  = new Order("B1", "BTC/USDT", "BUY",  new BigDecimal("100"), new BigDecimal("10"));
engine.processOrder(sell);
List<Order> trades = engine.processOrder(buy);
// 断言：trades.size() == 1，成交价=100，成交量=10

// 测试2：部分成交（买单数量 > 卖单数量）
// 意图：验证买单剩余部分正确挂入买单队列
Order sell = new Order("S1", "BTC/USDT", "SELL", new BigDecimal("100"), new BigDecimal("5"));
Order buy  = new Order("B1", "BTC/USDT", "BUY",  new BigDecimal("100"), new BigDecimal("10"));
engine.processOrder(sell);
List<Order> trades = engine.processOrder(buy);
// 断言：trades.size() == 1，成交量=5，买单剩余5挂在队列

// 测试3：价格不匹配，无法成交
// 意图：验证买价 < 卖价时不撮合，双方均入队
Order sell = new Order("S1", "BTC/USDT", "SELL", new BigDecimal("105"), new BigDecimal("10"));
Order buy  = new Order("B1", "BTC/USDT", "BUY",  new BigDecimal("100"), new BigDecimal("10"));
engine.processOrder(sell);
List<Order> trades = engine.processOrder(buy);
// 断言：trades.size() == 0

// 测试4：一笔买单匹配多笔卖单
// 意图：验证循环撮合逻辑，买单吃掉多档卖盘
Order sell1 = new Order("S1", "BTC/USDT", "SELL", new BigDecimal("99"),  new BigDecimal("3"));
Order sell2 = new Order("S2", "BTC/USDT", "SELL", new BigDecimal("100"), new BigDecimal("3"));
Order buy   = new Order("B1", "BTC/USDT", "BUY",  new BigDecimal("101"), new BigDecimal("10"));
engine.processOrder(sell1);
engine.processOrder(sell2);
List<Order> trades = engine.processOrder(buy);
// 断言：trades.size() == 2，总成交量=6，买单剩余4挂队列

// 测试5：成交价格规则（maker 定价）
// 意图：验证成交价取卖单价（先挂单方定价）
Order sell = new Order("S1", "BTC/USDT", "SELL", new BigDecimal("99"), new BigDecimal("10"));
Order buy  = new Order("B1", "BTC/USDT", "BUY",  new BigDecimal("101"), new BigDecimal("10"));
engine.processOrder(sell);
List<Order> trades = engine.processOrder(buy);
// 断言：trades.get(0).getPrice() == 99（卖单价，非买单价）

// 测试6：不同交易对互不干扰
// 意图：验证 symbol 隔离，BTC/USDT 的卖单不会匹配 ETH/USDT 的买单
Order sell = new Order("S1", "BTC/USDT", "SELL", new BigDecimal("100"), new BigDecimal("10"));
Order buy  = new Order("B1", "ETH/USDT", "BUY",  new BigDecimal("100"), new BigDecimal("10"));
engine.processOrder(sell);
List<Order> trades = engine.processOrder(buy);
// 断言：trades.size() == 0
```

---

### 6.3 maex-market 行情模块测试

**测试意图：验证订单簿深度数据的正确性和排序**

```java
// 测试1：买单按价格降序排列
// 意图：验证盘口展示时最优买价在最前
orderBook.updateBuyOrder(new BigDecimal("99"),  new BigDecimal("10"));
orderBook.updateBuyOrder(new BigDecimal("101"), new BigDecimal("5"));
orderBook.updateBuyOrder(new BigDecimal("100"), new BigDecimal("8"));
List<PriceLevel> buys = orderBook.getBuyOrders(3);
// 断言：buys[0].price == 101，buys[1].price == 100，buys[2].price == 99

// 测试2：卖单按价格升序排列
// 意图：验证最优卖价（最低卖价）在最前
orderBook.updateSellOrder(new BigDecimal("102"), new BigDecimal("10"));
orderBook.updateSellOrder(new BigDecimal("100"), new BigDecimal("5"));
List<PriceLevel> sells = orderBook.getSellOrders(2);
// 断言：sells[0].price == 100，sells[1].price == 102

// 测试3：数量为0时移除价位
// 意图：验证撤单后该价位从深度图消失
orderBook.updateBuyOrder(new BigDecimal("100"), new BigDecimal("10"));
orderBook.updateBuyOrder(new BigDecimal("100"), BigDecimal.ZERO);
List<PriceLevel> buys = orderBook.getBuyOrders(10);
// 断言：buys 中不含 price==100 的档位

// 测试4：depth 参数限制返回档数
// 意图：验证 API 返回档数受控，避免数据过大
// 插入10个价位，getBuyOrders(5) 只返回5档
// 断言：buys.size() == 5

// 测试5：recordTrade 更新最新成交价
// 意图：验证成交后行情数据实时更新
orderBook.recordTrade(new BigDecimal("100.5"), new BigDecimal("3"));
// 断言：orderBook.getLastTradePrice() == 100.5
//       orderBook.getLastTradeQuantity() == 3
```

---

### 6.4 maex-clearing 清算模块测试

**测试意图：验证资金/资产划转的正确性和异常保护**

```java
// 测试1：买入清算 - 资金扣除，资产增加
// 意图：验证 BUY 方向的清算逻辑
clearingService.deposit("ACC1", new BigDecimal("10000"));
Order buyTrade = new Order("T1", "BTC/USDT", "BUY", new BigDecimal("100"), new BigDecimal("5"));
// 手动触发 clearOrder（通过 pendingOrders 队列）
// 断言：FUNDS 余额减少 500（100*5），BTC/USDT 资产增加 5

// 测试2：卖出清算 - 资产扣除，资金增加
// 意图：验证 SELL 方向的清算逻辑
// 先给账户充入资产，再卖出
// 断言：资产减少，FUNDS 增加

// 测试3：资金不足抛出异常
// 意图：验证风控保护，防止超额清算
clearingService.deposit("ACC2", new BigDecimal("100")); // 只有100
Order bigBuy = new Order("T2", "BTC/USDT", "BUY", new BigDecimal("200"), new BigDecimal("5")); // 需要1000
// 断言：抛出 InsufficientFundsException

// 测试4：资产不足抛出异常
// 意图：验证卖出时持仓检查
// 账户无 BTC 持仓，尝试卖出
// 断言：抛出 InsufficientAssetsException

// 测试5：幂等性 - 同一笔交易不重复清算
// 意图：防止网络重试导致重复扣款
clearingService.submitOrderForClearing(trade);
clearingService.submitOrderForClearing(trade); // 重复提交
// 断言：第二次提交被忽略（warn 日志），余额只变动一次

// 测试6：存款/提现基础操作
clearingService.deposit("ACC3", new BigDecimal("1000"));
// 断言：getAccountBalance("ACC3", "FUNDS") == 1000
clearingService.withdraw("ACC3", new BigDecimal("300"));
// 断言：getAccountBalance("ACC3", "FUNDS") == 700

// 测试7：提现金额为负数抛出异常
// 意图：验证入参校验
// 断言：抛出 IllegalArgumentException
```

---

### 6.5 集成测试：撮合 + 清算联动

**测试意图：验证从下单到资金结算的完整链路**

```java
// 场景：Alice 卖出 BTC，Bob 买入 BTC，验证双方账户变化
// 初始状态：
//   Alice: FUNDS=0,    BTC=10
//   Bob:   FUNDS=5000, BTC=0

// 步骤1：Alice 挂卖单 10 BTC @ 100
Order aliceSell = new Order("ALICE-S1", "BTC/USDT", "SELL", new BigDecimal("100"), new BigDecimal("10"));
engine.processOrder(aliceSell);

// 步骤2：Bob 挂买单 10 BTC @ 100
Order bobBuy = new Order("BOB-B1", "BTC/USDT", "BUY", new BigDecimal("100"), new BigDecimal("10"));
List<Order> trades = engine.processOrder(bobBuy);

// 步骤3：将成交结果送入清算
for (Order trade : trades) {
    clearingService.submitOrderForClearing(trade);
}
clearingService.processPendingTrades();

// 断言最终状态：
// Alice: FUNDS=1000, BTC=0   （卖出10 BTC，获得 10*100=1000 资金）
// Bob:   FUNDS=4000, BTC=10  （买入10 BTC，花费 10*100=1000 资金）
```

---

## 七、已知设计局限（供扩展参考）

| 问题 | 当前实现 | 生产级方案 |
|------|----------|------------|
| 撮合引擎无持久化 | 内存 PriorityQueue | Redis / 数据库持久化 |
| 清算无双边账户 | 单账户 Map | 买卖双方账户联动 |
| 无订单状态机 | 无 status 字段 | PENDING/PARTIAL/FILLED/CANCELLED |
| 无撤单功能 | 不支持 | 从队列中移除指定 orderId |
| 行情与引擎解耦 | 各自独立 | 引擎成交后推送事件给行情模块 |
| 无并发保护 | synchronized/ConcurrentMap | 单线程撮合 + 无锁队列（Disruptor）|

---

*文档生成时间：2026-03-12*
