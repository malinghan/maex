package com.maex.market;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OrderBook 单元测试
 */
public class OrderBookTest {

    private OrderBook orderBook;

    @Before
    public void setUp() {
        orderBook = new OrderBook("BTC/USDT");
    }

    @Test
    public void testOrderBookCreation() {
        assertNotNull("OrderBook should not be null", orderBook);
        assertEquals("Symbol should match", "BTC/USDT", orderBook.getSymbol());
        assertTrue("Last update time should be set", orderBook.getLastUpdateTime() > 0);
    }

    @Test
    public void testUpdateBuyOrder() {
        BigDecimal price = new BigDecimal("50000");
        BigDecimal quantity = new BigDecimal("1.5");

        orderBook.updateBuyOrder(price, quantity);

        List<OrderBook.PriceLevel> buyOrders = orderBook.getBuyOrders(10);
        assertEquals("Should have one buy order", 1, buyOrders.size());
        assertEquals("Price should match", 0, price.compareTo(buyOrders.get(0).getPrice()));
        assertEquals("Quantity should match", 0, quantity.compareTo(buyOrders.get(0).getQuantity()));
    }

    @Test
    public void testUpdateSellOrder() {
        BigDecimal price = new BigDecimal("51000");
        BigDecimal quantity = new BigDecimal("2.0");

        orderBook.updateSellOrder(price, quantity);

        List<OrderBook.PriceLevel> sellOrders = orderBook.getSellOrders(10);
        assertEquals("Should have one sell order", 1, sellOrders.size());
        assertEquals("Price should match", 0, price.compareTo(sellOrders.get(0).getPrice()));
        assertEquals("Quantity should match", 0, quantity.compareTo(sellOrders.get(0).getQuantity()));
    }

    @Test
    public void testRemoveBuyOrder() {
        BigDecimal price = new BigDecimal("50000");
        orderBook.updateBuyOrder(price, new BigDecimal("1.5"));
        orderBook.updateBuyOrder(price, BigDecimal.ZERO);

        List<OrderBook.PriceLevel> buyOrders = orderBook.getBuyOrders(10);
        assertEquals("Buy order should be removed", 0, buyOrders.size());
    }

    @Test
    public void testRemoveSellOrder() {
        BigDecimal price = new BigDecimal("51000");
        orderBook.updateSellOrder(price, new BigDecimal("2.0"));
        orderBook.updateSellOrder(price, BigDecimal.ZERO);

        List<OrderBook.PriceLevel> sellOrders = orderBook.getSellOrders(10);
        assertEquals("Sell order should be removed", 0, sellOrders.size());
    }

    @Test
    public void testBuyOrdersSortedDescending() {
        orderBook.updateBuyOrder(new BigDecimal("50000"), new BigDecimal("1"));
        orderBook.updateBuyOrder(new BigDecimal("51000"), new BigDecimal("1"));
        orderBook.updateBuyOrder(new BigDecimal("49000"), new BigDecimal("1"));

        List<OrderBook.PriceLevel> buyOrders = orderBook.getBuyOrders(10);

        assertEquals("Should have 3 buy orders", 3, buyOrders.size());
        assertEquals("First should be highest price", 0,
                    new BigDecimal("51000").compareTo(buyOrders.get(0).getPrice()));
        assertEquals("Second should be middle price", 0,
                    new BigDecimal("50000").compareTo(buyOrders.get(1).getPrice()));
        assertEquals("Third should be lowest price", 0,
                    new BigDecimal("49000").compareTo(buyOrders.get(2).getPrice()));
    }

    @Test
    public void testSellOrdersSortedAscending() {
        orderBook.updateSellOrder(new BigDecimal("51000"), new BigDecimal("1"));
        orderBook.updateSellOrder(new BigDecimal("50000"), new BigDecimal("1"));
        orderBook.updateSellOrder(new BigDecimal("52000"), new BigDecimal("1"));

        List<OrderBook.PriceLevel> sellOrders = orderBook.getSellOrders(10);

        assertEquals("Should have 3 sell orders", 3, sellOrders.size());
        assertEquals("First should be lowest price", 0,
                    new BigDecimal("50000").compareTo(sellOrders.get(0).getPrice()));
        assertEquals("Second should be middle price", 0,
                    new BigDecimal("51000").compareTo(sellOrders.get(1).getPrice()));
        assertEquals("Third should be highest price", 0,
                    new BigDecimal("52000").compareTo(sellOrders.get(2).getPrice()));
    }

    @Test
    public void testGetOrdersWithDepthLimit() {
        for (int i = 0; i < 10; i++) {
            orderBook.updateBuyOrder(new BigDecimal(50000 + i * 100), new BigDecimal("1"));
        }

        List<OrderBook.PriceLevel> buyOrders = orderBook.getBuyOrders(5);

        assertEquals("Should return only 5 orders", 5, buyOrders.size());
    }

    @Test
    public void testRecordTrade() {
        BigDecimal price = new BigDecimal("50000");
        BigDecimal quantity = new BigDecimal("1.5");

        orderBook.recordTrade(price, quantity);

        assertEquals("Last trade price should match", 0,
                    price.compareTo(orderBook.getLastTradePrice()));
        assertEquals("Last trade quantity should match", 0,
                    quantity.compareTo(orderBook.getLastTradeQuantity()));
    }

    @Test
    public void testLastUpdateTimeUpdates() {
        long initialTime = orderBook.getLastUpdateTime();

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        orderBook.updateBuyOrder(new BigDecimal("50000"), new BigDecimal("1"));

        assertTrue("Last update time should be updated",
                  orderBook.getLastUpdateTime() > initialTime);
    }

    @Test
    public void testMultiplePriceLevels() {
        orderBook.updateBuyOrder(new BigDecimal("50000"), new BigDecimal("1"));
        orderBook.updateBuyOrder(new BigDecimal("49900"), new BigDecimal("2"));
        orderBook.updateBuyOrder(new BigDecimal("49800"), new BigDecimal("3"));

        orderBook.updateSellOrder(new BigDecimal("50100"), new BigDecimal("1"));
        orderBook.updateSellOrder(new BigDecimal("50200"), new BigDecimal("2"));
        orderBook.updateSellOrder(new BigDecimal("50300"), new BigDecimal("3"));

        List<OrderBook.PriceLevel> buyOrders = orderBook.getBuyOrders(10);
        List<OrderBook.PriceLevel> sellOrders = orderBook.getSellOrders(10);

        assertEquals("Should have 3 buy price levels", 3, buyOrders.size());
        assertEquals("Should have 3 sell price levels", 3, sellOrders.size());
    }

    @Test
    public void testUpdateExistingPriceLevel() {
        BigDecimal price = new BigDecimal("50000");
        orderBook.updateBuyOrder(price, new BigDecimal("1"));
        orderBook.updateBuyOrder(price, new BigDecimal("2"));

        List<OrderBook.PriceLevel> buyOrders = orderBook.getBuyOrders(10);

        assertEquals("Should have one price level", 1, buyOrders.size());
        assertEquals("Quantity should be updated", 0,
                    new BigDecimal("2").compareTo(buyOrders.get(0).getQuantity()));
    }

    @Test
    public void testConcurrentUpdates() throws InterruptedException {
        int threadCount = 10;
        int updatesPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < updatesPerThread; j++) {
                        BigDecimal price = new BigDecimal(50000 + threadId * 100 + j);
                        BigDecimal quantity = new BigDecimal("0.1");
                        if (threadId % 2 == 0) {
                            orderBook.updateBuyOrder(price, quantity);
                        } else {
                            orderBook.updateSellOrder(price, quantity);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertTrue("Concurrent updates completed", true);
    }

    @Test
    public void testOrderBookToString() {
        orderBook.updateBuyOrder(new BigDecimal("50000"), new BigDecimal("1"));
        orderBook.updateSellOrder(new BigDecimal("51000"), new BigDecimal("1"));

        String orderBookString = orderBook.toString();

        assertTrue("Should contain symbol", orderBookString.contains("BTC/USDT"));
        assertTrue("Should contain buy orders size", orderBookString.contains("buyOrdersSize"));
        assertTrue("Should contain sell orders size", orderBookString.contains("sellOrdersSize"));
    }

    @Test
    public void testPriceLevelToString() {
        OrderBook.PriceLevel priceLevel = new OrderBook.PriceLevel(
            new BigDecimal("50000"), new BigDecimal("1.5")
        );

        String priceLevelString = priceLevel.toString();

        assertTrue("Should contain price", priceLevelString.contains("50000"));
        assertTrue("Should contain quantity", priceLevelString.contains("1.5"));
    }

    @Test
    public void testEmptyOrderBook() {
        List<OrderBook.PriceLevel> buyOrders = orderBook.getBuyOrders(10);
        List<OrderBook.PriceLevel> sellOrders = orderBook.getSellOrders(10);

        assertEquals("Buy orders should be empty", 0, buyOrders.size());
        assertEquals("Sell orders should be empty", 0, sellOrders.size());
        assertNull("Last trade price should be null", orderBook.getLastTradePrice());
        assertNull("Last trade quantity should be null", orderBook.getLastTradeQuantity());
    }
}
