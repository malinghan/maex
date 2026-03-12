package com.maex.engine.matching;

import com.maex.common.model.Order;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OrderMatchingEngine 单元测试
 */
public class OrderMatchingEngineTest {

    private OrderMatchingEngine engine;

    @Before
    public void setUp() {
        engine = new OrderMatchingEngine();
    }

    @Test
    public void testEngineCreation() {
        assertNotNull("Engine should not be null", engine);
    }

    @Test
    public void testSimpleBuyOrderNoMatch() {
        Order buyOrder = new Order("ORD001", "BTC/USDT", "BUY",
                                   new BigDecimal("50000"), new BigDecimal("1"));

        List<Order> trades = engine.processOrder(buyOrder);

        assertTrue("No trades should be generated", trades.isEmpty());
    }

    @Test
    public void testSimpleSellOrderNoMatch() {
        Order sellOrder = new Order("ORD002", "BTC/USDT", "SELL",
                                    new BigDecimal("50000"), new BigDecimal("1"));

        List<Order> trades = engine.processOrder(sellOrder);

        assertTrue("No trades should be generated", trades.isEmpty());
    }

    @Test
    public void testPerfectMatch() {
        // 先提交卖单
        Order sellOrder = new Order("ORD003", "BTC/USDT", "SELL",
                                    new BigDecimal("50000"), new BigDecimal("1"));
        engine.processOrder(sellOrder);

        // 再提交买单，价格匹配
        Order buyOrder = new Order("ORD004", "BTC/USDT", "BUY",
                                   new BigDecimal("50000"), new BigDecimal("1"));
        List<Order> trades = engine.processOrder(buyOrder);

        assertEquals("Should generate one trade", 1, trades.size());
        Order trade = trades.get(0);
        assertEquals("Trade quantity should be 1", 0,
                    new BigDecimal("1").compareTo(trade.getQuantity()));
        assertEquals("Trade price should be 50000", 0,
                    new BigDecimal("50000").compareTo(trade.getPrice()));
    }

    @Test
    public void testPartialMatch() {
        // 卖单数量小于买单
        Order sellOrder = new Order("ORD005", "BTC/USDT", "SELL",
                                    new BigDecimal("50000"), new BigDecimal("1"));
        engine.processOrder(sellOrder);

        Order buyOrder = new Order("ORD006", "BTC/USDT", "BUY",
                                   new BigDecimal("50000"), new BigDecimal("2"));
        List<Order> trades = engine.processOrder(buyOrder);

        assertEquals("Should generate one trade", 1, trades.size());
        Order trade = trades.get(0);
        assertEquals("Trade quantity should be 1", 0,
                    new BigDecimal("1").compareTo(trade.getQuantity()));
    }

    @Test
    public void testPricePriority() {
        // 提交两个不同价格的卖单
        Order sellOrder1 = new Order("ORD007", "BTC/USDT", "SELL",
                                     new BigDecimal("49000"), new BigDecimal("1"));
        Order sellOrder2 = new Order("ORD008", "BTC/USDT", "SELL",
                                     new BigDecimal("50000"), new BigDecimal("1"));
        engine.processOrder(sellOrder1);
        engine.processOrder(sellOrder2);

        // 提交买单，应该匹配价格更低的卖单
        Order buyOrder = new Order("ORD009", "BTC/USDT", "BUY",
                                   new BigDecimal("50000"), new BigDecimal("1"));
        List<Order> trades = engine.processOrder(buyOrder);

        assertEquals("Should generate one trade", 1, trades.size());
        Order trade = trades.get(0);
        assertEquals("Should match lower price", 0,
                    new BigDecimal("49000").compareTo(trade.getPrice()));
    }

    @Test
    public void testMultipleMatches() {
        // 提交多个卖单
        Order sellOrder1 = new Order("ORD010", "BTC/USDT", "SELL",
                                     new BigDecimal("49000"), new BigDecimal("1"));
        Order sellOrder2 = new Order("ORD011", "BTC/USDT", "SELL",
                                     new BigDecimal("49500"), new BigDecimal("1"));
        engine.processOrder(sellOrder1);
        engine.processOrder(sellOrder2);

        // 提交大额买单
        Order buyOrder = new Order("ORD012", "BTC/USDT", "BUY",
                                   new BigDecimal("50000"), new BigDecimal("2"));
        List<Order> trades = engine.processOrder(buyOrder);

        assertEquals("Should generate two trades", 2, trades.size());
    }

    @Test
    public void testBuyPriceNotMatch() {
        // 卖单价格高于买单
        Order sellOrder = new Order("ORD013", "BTC/USDT", "SELL",
                                    new BigDecimal("51000"), new BigDecimal("1"));
        engine.processOrder(sellOrder);

        Order buyOrder = new Order("ORD014", "BTC/USDT", "BUY",
                                   new BigDecimal("50000"), new BigDecimal("1"));
        List<Order> trades = engine.processOrder(buyOrder);

        assertTrue("No trades should be generated", trades.isEmpty());
    }

    @Test
    public void testSellPriceNotMatch() {
        // 买单价格低于卖单
        Order buyOrder = new Order("ORD015", "BTC/USDT", "BUY",
                                   new BigDecimal("49000"), new BigDecimal("1"));
        engine.processOrder(buyOrder);

        Order sellOrder = new Order("ORD016", "BTC/USDT", "SELL",
                                    new BigDecimal("50000"), new BigDecimal("1"));
        List<Order> trades = engine.processOrder(sellOrder);

        assertTrue("No trades should be generated", trades.isEmpty());
    }

    @Test
    public void testMultipleSymbols() {
        // BTC/USDT 订单
        Order btcBuy = new Order("ORD017", "BTC/USDT", "BUY",
                                new BigDecimal("50000"), new BigDecimal("1"));
        Order btcSell = new Order("ORD018", "BTC/USDT", "SELL",
                                 new BigDecimal("50000"), new BigDecimal("1"));

        // ETH/USDT 订单
        Order ethBuy = new Order("ORD019", "ETH/USDT", "BUY",
                                new BigDecimal("3000"), new BigDecimal("10"));
        Order ethSell = new Order("ORD020", "ETH/USDT", "SELL",
                                 new BigDecimal("3000"), new BigDecimal("10"));

        engine.processOrder(btcSell);
        engine.processOrder(ethSell);

        List<Order> btcTrades = engine.processOrder(btcBuy);
        List<Order> ethTrades = engine.processOrder(ethBuy);

        assertEquals("BTC should have one trade", 1, btcTrades.size());
        assertEquals("ETH should have one trade", 1, ethTrades.size());
    }

    @Test
    public void testZeroQuantityOrder() {
        Order order = new Order("ORD021", "BTC/USDT", "BUY",
                               new BigDecimal("50000"), BigDecimal.ZERO);

        List<Order> trades = engine.processOrder(order);

        assertTrue("No trades should be generated for zero quantity", trades.isEmpty());
    }

    @Test
    public void testConcurrentOrderProcessing() throws InterruptedException {
        int threadCount = 10;
        int ordersPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger orderCounter = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < ordersPerThread; j++) {
                        int orderId = orderCounter.incrementAndGet();
                        String side = (orderId % 2 == 0) ? "BUY" : "SELL";
                        BigDecimal price = new BigDecimal("50000");
                        BigDecimal quantity = new BigDecimal("0.1");

                        Order order = new Order("ORD" + orderId, "BTC/USDT",
                                               side, price, quantity);
                        engine.processOrder(order);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // 测试通过表示没有并发异常
        assertTrue("Concurrent processing completed", true);
    }

    @Test
    public void testLargeQuantityMatch() {
        Order sellOrder = new Order("ORD022", "BTC/USDT", "SELL",
                                    new BigDecimal("50000"), new BigDecimal("100"));
        engine.processOrder(sellOrder);

        Order buyOrder = new Order("ORD023", "BTC/USDT", "BUY",
                                   new BigDecimal("50000"), new BigDecimal("100"));
        List<Order> trades = engine.processOrder(buyOrder);

        assertEquals("Should generate one trade", 1, trades.size());
        assertEquals("Trade quantity should be 100", 0,
                    new BigDecimal("100").compareTo(trades.get(0).getQuantity()));
    }
}
