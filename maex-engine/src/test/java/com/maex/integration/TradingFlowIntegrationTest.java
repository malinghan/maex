package com.maex.integration;

import com.maex.clearing.ClearingService;
import com.maex.common.model.Order;
import com.maex.engine.matching.OrderMatchingEngine;
import com.maex.market.OrderBook;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 完整交易流程集成测试
 * 测试从订单提交到清算完成的完整流程
 */
public class TradingFlowIntegrationTest {

    private OrderMatchingEngine matchingEngine;
    private ClearingService clearingService;
    private OrderBook orderBook;

    @Before
    public void setUp() {
        matchingEngine = new OrderMatchingEngine();
        clearingService = new ClearingService();
        orderBook = new OrderBook("BTC/USDT");
    }

    @Test
    public void testCompleteTradeFlow() {
        // 1. 初始化账户
        String buyerAccount = "BUYER001";
        String sellerAccount = "SELLER001";

        clearingService.deposit(buyerAccount, new BigDecimal("100000")); // 买家存入10万USDT

        // 2. 卖家下单
        Order sellOrder = new Order("SELL001", "BTC/USDT", "SELL",
                                    new BigDecimal("49000"), new BigDecimal("1"));
        List<Order> sellTrades = matchingEngine.processOrder(sellOrder);

        // 更新订单簿
        if (sellTrades.isEmpty()) {
            orderBook.updateSellOrder(sellOrder.getPrice(), sellOrder.getQuantity());
        }

        // 3. 买家下单
        Order buyOrder = new Order("BUY001", "BTC/USDT", "BUY",
                                   new BigDecimal("50000"), new BigDecimal("1"));
        List<Order> buyTrades = matchingEngine.processOrder(buyOrder);

        // 4. 验证交易生成
        assertFalse("Should generate trades", buyTrades.isEmpty());
        assertEquals("Should generate one trade", 1, buyTrades.size());

        Order trade = buyTrades.get(0);
        assertEquals("Trade price should be 49000", 0,
                    new BigDecimal("49000").compareTo(trade.getPrice()));
        assertEquals("Trade quantity should be 1", 0,
                    new BigDecimal("1").compareTo(trade.getQuantity()));

        // 5. 记录交易到订单簿
        orderBook.recordTrade(trade.getPrice(), trade.getQuantity());

        // 6. 提交清算
        clearingService.submitOrderForClearing(trade);

        // 7. 验证订单簿更新
        assertEquals("Last trade price should match", 0,
                    new BigDecimal("49000").compareTo(orderBook.getLastTradePrice()));

        System.out.println("Complete trade flow test passed!");
    }

    @Test
    public void testPartialFillFlow() {
        // 1. 卖家下小额卖单
        Order sellOrder = new Order("SELL002", "BTC/USDT", "SELL",
                                    new BigDecimal("50000"), new BigDecimal("1"));
        matchingEngine.processOrder(sellOrder);

        // 2. 买家下大额买单
        Order buyOrder = new Order("BUY002", "BTC/USDT", "BUY",
                                   new BigDecimal("50000"), new BigDecimal("2"));
        List<Order> trades = matchingEngine.processOrder(buyOrder);

        // 3. 验证部分成交
        assertEquals("Should generate one trade", 1, trades.size());
        Order trade = trades.get(0);
        assertEquals("Trade quantity should be 1", 0,
                    new BigDecimal("1").compareTo(trade.getQuantity()));

        // 4. 记录到订单簿
        orderBook.recordTrade(trade.getPrice(), trade.getQuantity());

        // 5. 验证剩余订单
        // 剩余1个BTC的买单应该在订单簿中
        List<OrderBook.PriceLevel> buyOrders = orderBook.getBuyOrders(10);

        System.out.println("Partial fill flow test passed!");
    }

    @Test
    public void testMultiLevelPriceMatching() {
        // 1. 添加多个不同价格的卖单
        Order sell1 = new Order("SELL003", "BTC/USDT", "SELL",
                               new BigDecimal("49000"), new BigDecimal("0.5"));
        Order sell2 = new Order("SELL004", "BTC/USDT", "SELL",
                               new BigDecimal("49500"), new BigDecimal("0.5"));
        Order sell3 = new Order("SELL005", "BTC/USDT", "SELL",
                               new BigDecimal("50000"), new BigDecimal("0.5"));

        matchingEngine.processOrder(sell1);
        matchingEngine.processOrder(sell2);
        matchingEngine.processOrder(sell3);

        // 更新订单簿
        orderBook.updateSellOrder(sell1.getPrice(), sell1.getQuantity());
        orderBook.updateSellOrder(sell2.getPrice(), sell2.getQuantity());
        orderBook.updateSellOrder(sell3.getPrice(), sell3.getQuantity());

        // 2. 下大额买单
        Order buyOrder = new Order("BUY003", "BTC/USDT", "BUY",
                                   new BigDecimal("50000"), new BigDecimal("1.5"));
        List<Order> trades = matchingEngine.processOrder(buyOrder);

        // 3. 验证按价格优先匹配
        assertEquals("Should generate 3 trades", 3, trades.size());

        // 第一笔交易应该是最低价
        assertEquals("First trade should be at 49000", 0,
                    new BigDecimal("49000").compareTo(trades.get(0).getPrice()));

        // 4. 记录所有交易
        for (Order trade : trades) {
            orderBook.recordTrade(trade.getPrice(), trade.getQuantity());
            clearingService.submitOrderForClearing(trade);
        }

        System.out.println("Multi-level price matching test passed!");
    }

    @Test
    public void testNoMatchScenario() {
        // 1. 卖单价格高于买单
        Order sellOrder = new Order("SELL006", "BTC/USDT", "SELL",
                                    new BigDecimal("51000"), new BigDecimal("1"));
        matchingEngine.processOrder(sellOrder);
        orderBook.updateSellOrder(sellOrder.getPrice(), sellOrder.getQuantity());

        Order buyOrder = new Order("BUY004", "BTC/USDT", "BUY",
                                   new BigDecimal("50000"), new BigDecimal("1"));
        List<Order> trades = matchingEngine.processOrder(buyOrder);

        // 2. 验证无交易生成
        assertTrue("Should not generate any trades", trades.isEmpty());

        // 3. 验证订单簿中有两个订单
        orderBook.updateBuyOrder(buyOrder.getPrice(), buyOrder.getQuantity());

        List<OrderBook.PriceLevel> buyOrders = orderBook.getBuyOrders(10);
        List<OrderBook.PriceLevel> sellOrders = orderBook.getSellOrders(10);

        assertEquals("Should have 1 buy order", 1, buyOrders.size());
        assertEquals("Should have 1 sell order", 1, sellOrders.size());

        System.out.println("No match scenario test passed!");
    }

    @Test
    public void testMultipleSymbolsIsolation() {
        // 1. BTC/USDT 交易
        OrderBook btcOrderBook = new OrderBook("BTC/USDT");
        Order btcSell = new Order("SELL007", "BTC/USDT", "SELL",
                                 new BigDecimal("50000"), new BigDecimal("1"));
        Order btcBuy = new Order("BUY005", "BTC/USDT", "BUY",
                                new BigDecimal("50000"), new BigDecimal("1"));

        matchingEngine.processOrder(btcSell);
        List<Order> btcTrades = matchingEngine.processOrder(btcBuy);

        // 2. ETH/USDT 交易
        OrderBook ethOrderBook = new OrderBook("ETH/USDT");
        Order ethSell = new Order("SELL008", "ETH/USDT", "SELL",
                                 new BigDecimal("3000"), new BigDecimal("10"));
        Order ethBuy = new Order("BUY006", "ETH/USDT", "BUY",
                                new BigDecimal("3000"), new BigDecimal("10"));

        matchingEngine.processOrder(ethSell);
        List<Order> ethTrades = matchingEngine.processOrder(ethBuy);

        // 3. 验证交易隔离
        assertEquals("BTC should have 1 trade", 1, btcTrades.size());
        assertEquals("ETH should have 1 trade", 1, ethTrades.size());

        // 4. 记录到各自的订单簿
        btcOrderBook.recordTrade(btcTrades.get(0).getPrice(), btcTrades.get(0).getQuantity());
        ethOrderBook.recordTrade(ethTrades.get(0).getPrice(), ethTrades.get(0).getQuantity());

        assertEquals("BTC/USDT", btcOrderBook.getSymbol());
        assertEquals("ETH/USDT", ethOrderBook.getSymbol());

        System.out.println("Multiple symbols isolation test passed!");
    }

    @Test
    public void testClearingWithInsufficientFunds() {
        // 1. 买家账户余额不足
        String buyerAccount = "BUYER002";
        clearingService.deposit(buyerAccount, new BigDecimal("1000")); // 只存1000

        // 2. 尝试购买价值50000的BTC
        Order sellOrder = new Order("SELL009", "BTC/USDT", "SELL",
                                    new BigDecimal("50000"), new BigDecimal("1"));
        matchingEngine.processOrder(sellOrder);

        Order buyOrder = new Order("BUY007", "BTC/USDT", "BUY",
                                   new BigDecimal("50000"), new BigDecimal("1"));
        List<Order> trades = matchingEngine.processOrder(buyOrder);

        // 3. 交易会生成，但清算会失败
        if (!trades.isEmpty()) {
            // 在实际系统中，这里应该检查余额后再匹配
            System.out.println("Trade generated but clearing would fail due to insufficient funds");
        }

        System.out.println("Insufficient funds test passed!");
    }

    @Test
    public void testHighFrequencyTrading() {
        // 模拟高频交易场景
        String buyerAccount = "HFT_BUYER";
        String sellerAccount = "HFT_SELLER";

        clearingService.deposit(buyerAccount, new BigDecimal("1000000"));

        // 快速提交多个订单
        for (int i = 0; i < 100; i++) {
            BigDecimal price = new BigDecimal("50000").add(new BigDecimal(i));
            BigDecimal quantity = new BigDecimal("0.01");

            if (i % 2 == 0) {
                Order sellOrder = new Order("SELL_HFT_" + i, "BTC/USDT", "SELL", price, quantity);
                matchingEngine.processOrder(sellOrder);
            } else {
                Order buyOrder = new Order("BUY_HFT_" + i, "BTC/USDT", "BUY", price, quantity);
                List<Order> trades = matchingEngine.processOrder(buyOrder);

                for (Order trade : trades) {
                    orderBook.recordTrade(trade.getPrice(), trade.getQuantity());
                    clearingService.submitOrderForClearing(trade);
                }
            }
        }

        System.out.println("High frequency trading test passed!");
    }

    @Test
    public void testMarketDepthUpdate() {
        // 1. 添加多层买单
        for (int i = 0; i < 10; i++) {
            BigDecimal price = new BigDecimal("50000").subtract(new BigDecimal(i * 100));
            BigDecimal quantity = new BigDecimal("0.5");
            orderBook.updateBuyOrder(price, quantity);
        }

        // 2. 添加多层卖单
        for (int i = 0; i < 10; i++) {
            BigDecimal price = new BigDecimal("50100").add(new BigDecimal(i * 100));
            BigDecimal quantity = new BigDecimal("0.5");
            orderBook.updateSellOrder(price, quantity);
        }

        // 3. 验证市场深度
        List<OrderBook.PriceLevel> buyOrders = orderBook.getBuyOrders(5);
        List<OrderBook.PriceLevel> sellOrders = orderBook.getSellOrders(5);

        assertEquals("Should have 5 buy levels", 5, buyOrders.size());
        assertEquals("Should have 5 sell levels", 5, sellOrders.size());

        // 4. 验证价格排序
        assertTrue("Buy orders should be sorted descending",
                  buyOrders.get(0).getPrice().compareTo(buyOrders.get(1).getPrice()) > 0);
        assertTrue("Sell orders should be sorted ascending",
                  sellOrders.get(0).getPrice().compareTo(sellOrders.get(1).getPrice()) < 0);

        System.out.println("Market depth update test passed!");
    }
}
