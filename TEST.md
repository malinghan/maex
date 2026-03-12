# MAEX 交易系统测试文档

## 测试概述

本文档提供 MAEX 交易系统的完整测试流程、测试用例和可执行的测试代码。

## 测试环境要求

- JDK 17+
- Maven 3.6+
- JUnit 4.12
- Mockito 3.x (用于单元测试)

## 测试模块结构

```
maex/
├── maex-common/
│   └── src/test/java/
│       └── com/maex/common/model/
│           ├── OrderTest.java
│           └── AccountTest.java
├── maex-engine/
│   └── src/test/java/
│       └── com/maex/engine/matching/
│           └── OrderMatchingEngineTest.java
├── maex-market/
│   └── src/test/java/
│       └── com/maex/market/
│           └── OrderBookTest.java
└── maex-clearing/
    └── src/test/java/
        └── com/maex/clearing/
            └── ClearingServiceTest.java
```

## 测试执行命令

### 1. 运行所有测试
```bash
mvn clean test
```

### 2. 运行特定模块测试
```bash
# 测试 common 模块
mvn test -pl maex-common

# 测试 engine 模块
mvn test -pl maex-engine

# 测试 market 模块
mvn test -pl maex-market

# 测试 clearing 模块
mvn test -pl maex-clearing
```

### 3. 生成测试报告
```bash
mvn surefire-report:report
```

### 4. 查看测试覆盖率
```bash
mvn clean test jacoco:report
```

## 测试流程

### 阶段 1: 单元测试
测试各个模块的独立功能，确保每个类和方法按预期工作。

### 阶段 2: 集成测试
测试模块之间的交互，验证整个交易流程。

---

## 测试用例详细说明

### 1. maex-common 模块测试

#### 1.1 Order 类测试

**测试目标**: 验证订单对象的创建、属性设置和序列化功能

**测试用例**:
- ✅ 测试订单创建
- ✅ 测试订单属性设置和获取
- ✅ 测试订单序列化
- ✅ 测试订单时间戳自动生成

#### 1.2 Account 类测试

**测试目标**: 验证账户对象的创建和余额管理

**测试用例**:
- ✅ 测试账户创建
- ✅ 测试余额初始化
- ✅ 测试账户属性设置
- ✅ 测试时间戳自动生成

---

### 2. maex-engine 模块测试

#### 2.1 OrderMatchingEngine 测试

**测试目标**: 验证订单匹配引擎的核心功能

**测试用例**:
- ✅ 测试买单匹配
- ✅ 测试卖单匹配
- ✅ 测试部分成交
- ✅ 测试完全成交
- ✅ 测试价格优先原则
- ✅ 测试时间优先原则
- ✅ 测试无法匹配的订单
- ✅ 测试多个订单匹配
- ✅ 测试并发订单处理

**关键测试场景**:

1. **完全匹配场景**
   - 买单价格 >= 卖单价格
   - 买单数量 = 卖单数量
   - 预期: 完全成交

2. **部分匹配场景**
   - 买单价格 >= 卖单价格
   - 买单数量 > 卖单数量
   - 预期: 部分成交，剩余订单进入订单簿

3. **价格不匹配场景**
   - 买单价格 < 卖单价格
   - 预期: 订单进入订单簿等待匹配

---

### 3. maex-market 模块测试

#### 3.1 OrderBook 测试

**测试目标**: 验证订单簿的维护和市场深度功能

**测试用例**:
- ✅ 测试订单簿创建
- ✅ 测试买单更新
- ✅ 测试卖单更新
- ✅ 测试订单删除
- ✅ 测试市场深度查询
- ✅ 测试最新成交价记录
- ✅ 测试价格排序（买单降序，卖单升序）
- ✅ 测试并发更新

---

### 4. maex-clearing 模块测试

#### 4.1 ClearingService 测试

**测试目标**: 验证清算服务的资金和资产处理

**测试用例**:
- ✅ 测试交易提交
- ✅ 测试买入交易清算
- ✅ 测试卖出交易清算
- ✅ 测试资金不足异常
- ✅ 测试资产不足异常
- ✅ 测试存款功能
- ✅ 测试提现功能
- ✅ 测试账户余额查询
- ✅ 测试重复清算防护
- ✅ 测试并发清算

---

## 集成测试场景

### 场景 1: 完整交易流程测试

**流程**:
1. 用户 A 存入 10000 元
2. 用户 B 存入 100 股 BTC
3. 用户 A 下买单: BTC/USDT, 价格 50000, 数量 1
4. 用户 B 下卖单: BTC/USDT, 价格 49000, 数量 1
5. 订单匹配引擎匹配订单
6. 生成交易记录
7. 清算服务处理交易
8. 验证账户余额变化

**预期结果**:
- 用户 A: 余额减少 49000, BTC 增加 1
- 用户 B: 余额增加 49000, BTC 减少 1

### 场景 2: 部分成交测试

**流程**:
1. 用户 A 下买单: BTC/USDT, 价格 50000, 数量 2
2. 用户 B 下卖单: BTC/USDT, 价格 49000, 数量 1
3. 订单匹配，部分成交
4. 验证剩余订单进入订单簿

**预期结果**:
- 成交 1 个 BTC
- 用户 A 剩余买单 1 个 BTC 进入订单簿

### 场景 3: 多级价格匹配测试

**流程**:
1. 订单簿中存在多个不同价格的卖单
2. 用户下大额买单
3. 按价格优先原则依次匹配

**预期结果**:
- 按最优价格优先成交
- 成交价格符合价格优先原则

---

## 测试数据准备

### 测试账户
```
账户 ID: ACC001, 用户 ID: USER001, 余额: 100000 USDT
账户 ID: ACC002, 用户 ID: USER002, 余额: 10 BTC
账户 ID: ACC003, 用户 ID: USER003, 余额: 50000 USDT
账户 ID: ACC004, 用户 ID: USER004, 余额: 5 BTC
```

### 测试订单
```
订单 1: BUY, BTC/USDT, 价格 50000, 数量 1
订单 2: SELL, BTC/USDT, 价格 49000, 数量 1
订单 3: BUY, BTC/USDT, 价格 51000, 数量 2
订单 4: SELL, BTC/USDT, 价格 48000, 数量 0.5
```

---

## 测试检查清单

### 功能测试
- [ ] 所有单元测试通过
- [ ] 所有集成测试通过
- [ ] 边界条件测试通过
- [ ] 异常处理测试通过

### 安全测试
- [ ] 资金安全验证
- [ ] 并发安全验证
- [ ] 数据一致性验证

### 代码质量
- [ ] 测试覆盖率 > 80%
- [ ] 无严重代码质量问题
- [ ] 无内存泄漏

---

## 常见问题排查

### 1. 测试失败
- 检查测试数据是否正确
- 检查测试环境配置
- 查看详细错误日志

### 2. 并发问题
- 检查线程安全
- 使用并发测试工具
- 增加同步机制

---

## 测试报告模板

### 测试执行摘要
- 测试日期: YYYY-MM-DD
- 测试人员: XXX
- 测试环境: XXX
- 测试版本: X.X.X

### 测试结果统计
- 总测试用例数: XX
- 通过用例数: XX
- 失败用例数: XX
- 跳过用例数: XX
- 测试覆盖率: XX%

### 缺陷统计
- 严重缺陷: X
- 一般缺陷: X
- 轻微缺陷: X

### 测试结论
- [ ] 通过
- [ ] 不通过
- [ ] 有条件通过

---

## 附录: 测试最佳实践

1. **测试隔离**: 每个测试用例应该独立，不依赖其他测试
2. **测试数据**: 使用固定的测试数据，避免随机性
3. **测试命名**: 使用清晰的测试方法名，描述测试内容
4. **断言明确**: 每个测试应该有明确的断言
5. **异常测试**: 不仅测试正常流程，也要测试异常情况
6. **性能基准**: 建立性能基准，持续监控性能变化
7. **持续集成**: 将测试集成到 CI/CD 流程中
8. **测试文档**: 保持测试文档更新，与代码同步

---

## 测试代码文件清单

### 单元测试
- `maex-common/src/test/java/com/maex/common/model/OrderTest.java` - Order 类测试
- `maex-common/src/test/java/com/maex/common/model/AccountTest.java` - Account 类测试
- `maex-engine/src/test/java/com/maex/engine/matching/OrderMatchingEngineTest.java` - 订单匹配引擎测试
- `maex-market/src/test/java/com/maex/market/OrderBookTest.java` - 订单簿测试
- `maex-clearing/src/test/java/com/maex/clearing/ClearingServiceTest.java` - 清算服务测试

### 集成测试
- `maex-engine/src/test/java/com/maex/integration/TradingFlowIntegrationTest.java` - 完整交易流程集成测试

---

## 快速开始

### 1. 编译项目
```bash
cd /Users/malinghan/project/maex
mvn clean compile
```

### 2. 运行所有测试
```bash
mvn clean test
```

### 3. 查看测试结果
测试结果会输出到控制台，同时生成测试报告在 `target/surefire-reports/` 目录下。

### 4. 运行特定测试类
```bash
# 运行订单匹配引擎测试
mvn test -Dtest=OrderMatchingEngineTest

# 运行集成测试
mvn test -Dtest=TradingFlowIntegrationTest
```

---

## 测试覆盖的功能点

### OrderMatchingEngine (订单匹配引擎)
✅ 引擎创建
✅ 买单无匹配
✅ 卖单无匹配
✅ 完全匹配
✅ 部分匹配
✅ 价格优先原则
✅ 多个订单匹配
✅ 价格不匹配
✅ 多交易对隔离
✅ 零数量订单处理
✅ 并发订单处理
✅ 大数量订单匹配

### OrderBook (订单簿)
✅ 订单簿创建
✅ 买单更新
✅ 卖单更新
✅ 订单删除
✅ 买单降序排序
✅ 卖单升序排序
✅ 深度限制查询
✅ 交易记录
✅ 更新时间追踪
✅ 多价格层级
✅ 价格层级更新
✅ 并发更新
✅ 空订单簿处理

### ClearingService (清算服务)
✅ 服务创建
✅ 存款功能
✅ 多次存款
✅ 负数存款异常
✅ 零金额存款异常
✅ 提现功能
✅ 余额不足异常
✅ 负数提现异常
✅ 订单提交清算
✅ 空订单处理
✅ 重复订单处理
✅ 账户余额查询
✅ 多资产管理
✅ 并发存款
✅ 并发提现
✅ 多账户独立性
✅ 大额处理
✅ 小额处理

### Account & Order (数据模型)
✅ 对象创建
✅ 属性设置和获取
✅ 时间戳自动生成
✅ 余额一致性
✅ 序列化支持
✅ 字符串表示

### 集成测试场景
✅ 完整交易流程
✅ 部分成交流程
✅ 多级价格匹配
✅ 无匹配场景
✅ 多交易对隔离
✅ 余额不足处理
✅ 高频交易模拟
✅ 市场深度更新
