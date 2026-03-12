package com.maex.common.model;

import org.junit.Test;
import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order 类单元测试
 */
public class OrderTest {

    @Test
    public void testOrderCreation() {
        Order order = new Order();
        assertNotNull("Order should not be null", order);
    }

    @Test
    public void testOrderWithParameters() {
        String orderId = "ORD001";
        String symbol = "BTC/USDT";
        String side = "BUY";
        BigDecimal price = new BigDecimal("50000");
        BigDecimal quantity = new BigDecimal("1.5");

        Order order = new Order(orderId, symbol, side, price, quantity);

        assertEquals("Order ID should match", orderId, order.getOrderId());
        assertEquals("Symbol should match", symbol, order.getSymbol());
        assertEquals("Side should match", side, order.getSide());
        assertEquals("Price should match", 0, price.compareTo(order.getPrice()));
        assertEquals("Quantity should match", 0, quantity.compareTo(order.getQuantity()));
        assertNotNull("Timestamp should be set", order.getTimestamp());
    }

    @Test
    public void testOrderSettersAndGetters() {
        Order order = new Order();

        order.setOrderId("ORD002");
        order.setSymbol("ETH/USDT");
        order.setSide("SELL");
        order.setPrice(new BigDecimal("3000"));
        order.setQuantity(new BigDecimal("10"));
        LocalDateTime now = LocalDateTime.now();
        order.setTimestamp(now);

        assertEquals("ORD002", order.getOrderId());
        assertEquals("ETH/USDT", order.getSymbol());
        assertEquals("SELL", order.getSide());
        assertEquals(0, new BigDecimal("3000").compareTo(order.getPrice()));
        assertEquals(0, new BigDecimal("10").compareTo(order.getQuantity()));
        assertEquals(now, order.getTimestamp());
    }

    @Test
    public void testOrderToString() {
        Order order = new Order("ORD003", "BTC/USDT", "BUY",
                               new BigDecimal("50000"), new BigDecimal("2"));

        String orderString = order.toString();

        assertTrue("Should contain orderId", orderString.contains("ORD003"));
        assertTrue("Should contain symbol", orderString.contains("BTC/USDT"));
        assertTrue("Should contain side", orderString.contains("BUY"));
    }

    @Test
    public void testOrderTimestampAutoGeneration() {
        LocalDateTime before = LocalDateTime.now();
        Order order = new Order("ORD004", "BTC/USDT", "BUY",
                               new BigDecimal("50000"), new BigDecimal("1"));
        LocalDateTime after = LocalDateTime.now();

        assertNotNull("Timestamp should be generated", order.getTimestamp());
        assertTrue("Timestamp should be after or equal to before time",
                  !order.getTimestamp().isBefore(before));
        assertTrue("Timestamp should be before or equal to after time",
                  !order.getTimestamp().isAfter(after));
    }

    @Test
    public void testOrderWithZeroQuantity() {
        Order order = new Order("ORD005", "BTC/USDT", "BUY",
                               new BigDecimal("50000"), BigDecimal.ZERO);

        assertEquals("Quantity should be zero", 0,
                    BigDecimal.ZERO.compareTo(order.getQuantity()));
    }

    @Test
    public void testOrderWithLargeNumbers() {
        BigDecimal largePrice = new BigDecimal("999999999.99");
        BigDecimal largeQuantity = new BigDecimal("1000000");

        Order order = new Order("ORD006", "BTC/USDT", "BUY",
                               largePrice, largeQuantity);

        assertEquals(0, largePrice.compareTo(order.getPrice()));
        assertEquals(0, largeQuantity.compareTo(order.getQuantity()));
    }
}
