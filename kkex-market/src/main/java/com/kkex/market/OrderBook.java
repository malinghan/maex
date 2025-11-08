package com.kkex.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * 订单簿 - 维护市场上的买单和卖单，提供当前市场深度信息
 */
public class OrderBook {
    private static final Logger logger = LoggerFactory.getLogger(OrderBook.class);
    
    private final String symbol;
    // 使用ConcurrentSkipListMap保证排序和线程安全
    // 买单按价格降序排列（最高价优先）
    private final NavigableMap<BigDecimal, BigDecimal> buyOrders = new ConcurrentSkipListMap<>(Comparator.reverseOrder());
    // 卖单按价格升序排列（最低价优先）
    private final NavigableMap<BigDecimal, BigDecimal> sellOrders = new ConcurrentSkipListMap<>();
    
    private BigDecimal lastTradePrice;
    private BigDecimal lastTradeQuantity;
    private long lastUpdateTime;

    public OrderBook(String symbol) {
        this.symbol = symbol;
        this.lastUpdateTime = System.currentTimeMillis();
        logger.info("Order book created for symbol: {}", symbol);
    }

    /**
     * 更新买单
     * @param price 价格
     * @param quantity 数量
     */
    public void updateBuyOrder(BigDecimal price, BigDecimal quantity) {
        if (quantity.compareTo(BigDecimal.ZERO) > 0) {
            buyOrders.put(price, quantity);
        } else {
            buyOrders.remove(price);
        }
        updateLastUpdateTime();
    }

    /**
     * 更新卖单
     * @param price 价格
     * @param quantity 数量
     */
    public void updateSellOrder(BigDecimal price, BigDecimal quantity) {
        if (quantity.compareTo(BigDecimal.ZERO) > 0) {
            sellOrders.put(price, quantity);
        } else {
            sellOrders.remove(price);
        }
        updateLastUpdateTime();
    }

    /**
     * 记录交易
     * @param price 交易价格
     * @param quantity 交易数量
     */
    public void recordTrade(BigDecimal price, BigDecimal quantity) {
        this.lastTradePrice = price;
        this.lastTradeQuantity = quantity;
        updateLastUpdateTime();
        logger.debug("Trade recorded for {}: price={}, quantity={}", symbol, price, quantity);
    }

    /**
     * 获取买单列表（按价格降序）
     * @param depth 深度，即返回的价格层级数量
     * @return 买单列表
     */
    public List<PriceLevel> getBuyOrders(int depth) {
        return getPriceLevels(buyOrders, depth);
    }

    /**
     * 获取卖单列表（按价格升序）
     * @param depth 深度，即返回的价格层级数量
     * @return 卖单列表
     */
    public List<PriceLevel> getSellOrders(int depth) {
        return getPriceLevels(sellOrders, depth);
    }

    private List<PriceLevel> getPriceLevels(NavigableMap<BigDecimal, BigDecimal> orders, int depth) {
        List<PriceLevel> priceLevels = new ArrayList<>();
        int count = 0;
        for (Map.Entry<BigDecimal, BigDecimal> entry : orders.entrySet()) {
            priceLevels.add(new PriceLevel(entry.getKey(), entry.getValue()));
            count++;
            if (count >= depth) {
                break;
            }
        }
        return priceLevels;
    }

    /**
     * 获取最新成交价
     * @return 最新成交价
     */
    public BigDecimal getLastTradePrice() {
        return lastTradePrice;
    }

    /**
     * 获取最新成交量
     * @return 最新成交量
     */
    public BigDecimal getLastTradeQuantity() {
        return lastTradeQuantity;
    }

    /**
     * 获取上次更新时间
     * @return 上次更新时间戳
     */
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    private void updateLastUpdateTime() {
        this.lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * 获取订单簿符号
     * @return 交易对符号
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * 价格层级 - 表示某个价格的订单总量
     */
    public static class PriceLevel {
        private final BigDecimal price;
        private final BigDecimal quantity;

        public PriceLevel(BigDecimal price, BigDecimal quantity) {
            this.price = price;
            this.quantity = quantity;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        @Override
        public String toString() {
            return "PriceLevel{" +
                    "price=" + price +
                    ", quantity=" + quantity +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "OrderBook{" +
                "symbol='" + symbol + '\'' +
                ", buyOrdersSize=" + buyOrders.size() +
                ", sellOrdersSize=" + sellOrders.size() +
                ", lastTradePrice=" + lastTradePrice +
                ", lastUpdateTime=" + lastUpdateTime +
                '}';
    }
}