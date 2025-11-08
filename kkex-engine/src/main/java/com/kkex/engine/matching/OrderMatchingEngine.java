package com.kkex.engine.matching;

import com.kkex.common.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;

/**
 * 订单匹配引擎 - 负责订单的匹配和交易生成
 */
public class OrderMatchingEngine {
    private static final Logger logger = LoggerFactory.getLogger(OrderMatchingEngine.class);
    
    // 按交易对存储买单和卖单
    private final Map<String, PriorityQueue<Order>> buyOrders = new ConcurrentHashMap<>();
    private final Map<String, PriorityQueue<Order>> sellOrders = new ConcurrentHashMap<>();

    public OrderMatchingEngine() {
        // 初始化日志
        logger.info("Order Matching Engine started");
    }

    /**
     * 处理新订单
     * @param order 待处理的订单
     * @return 生成的交易列表
     */
    public List<Order> processOrder(Order order) {
        List<Order> trades = new ArrayList<>();
        String symbol = order.getSymbol();

        // 初始化买卖单队列（如果不存在）
        buyOrders.computeIfAbsent(symbol, k -> new PriorityQueue<>(Comparator.comparing(Order::getPrice).reversed()));
        sellOrders.computeIfAbsent(symbol, k -> new PriorityQueue<>(Comparator.comparing(Order::getPrice)));

        if (order.getSide().equals("BUY")) {
            // 处理买单
            matchBuyOrder(order, trades);
        } else if (order.getSide().equals("SELL")) {
            // 处理卖单
            matchSellOrder(order, trades);
        }

        return trades;
    }

    private void matchBuyOrder(Order buyOrder, List<Order> trades) {
        String symbol = buyOrder.getSymbol();
        PriorityQueue<Order> sellQueue = sellOrders.get(symbol);

        // 尝试匹配现有卖单
        while (!sellQueue.isEmpty() && buyOrder.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            Order sellOrder = sellQueue.peek();
            if (sellOrder.getPrice().compareTo(buyOrder.getPrice()) <= 0) {
                // 价格匹配，可以成交
                BigDecimal tradeQuantity = sellOrder.getQuantity().min(buyOrder.getQuantity());
                BigDecimal tradePrice = sellOrder.getPrice();

                // 创建交易记录
                Order trade = new Order(
                        buyOrder.getOrderId(),
                        symbol,
                        "BUY",
                        tradePrice,
                        tradeQuantity
                );
                trades.add(trade);

                // 更新订单数量
                sellOrder.setQuantity(sellOrder.getQuantity().subtract(tradeQuantity));
                buyOrder.setQuantity(buyOrder.getQuantity().subtract(tradeQuantity));

                // 如果卖单已完全成交，从队列中移除
                if (sellOrder.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    sellQueue.poll();
                }
            } else {
                // 价格不匹配，结束匹配
                break;
            }
        }

        // 如果买单未完全成交，加入买单队列
        if (buyOrder.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            buyOrders.get(symbol).offer(buyOrder);
        }
    }

    private void matchSellOrder(Order sellOrder, List<Order> trades) {
        String symbol = sellOrder.getSymbol();
        PriorityQueue<Order> buyQueue = buyOrders.get(symbol);

        // 尝试匹配现有买单
        while (!buyQueue.isEmpty() && sellOrder.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            Order buyOrder = buyQueue.peek();
            if (buyOrder.getPrice().compareTo(sellOrder.getPrice()) >= 0) {
                // 价格匹配，可以成交
                BigDecimal tradeQuantity = buyOrder.getQuantity().min(sellOrder.getQuantity());
                BigDecimal tradePrice = buyOrder.getPrice();

                // 创建交易记录
                Order trade = new Order(
                        sellOrder.getOrderId(),
                        symbol,
                        "SELL",
                        tradePrice,
                        tradeQuantity
                );
                trades.add(trade);

                // 更新订单数量
                buyOrder.setQuantity(buyOrder.getQuantity().subtract(tradeQuantity));
                sellOrder.setQuantity(sellOrder.getQuantity().subtract(tradeQuantity));

                // 如果买单已完全成交，从队列中移除
                if (buyOrder.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    buyQueue.poll();
                }
            } else {
                // 价格不匹配，结束匹配
                break;
            }
        }

        // 如果卖单未完全成交，加入卖单队列
        if (sellOrder.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            sellOrders.get(symbol).offer(sellOrder);
        }
    }

    private String generateTradeId() {
        return "TRD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 订单类 - 定义订单的基本属性
     */
    @Getter
    @Setter
    public static class Order {
        private String orderId;
        private String symbol;
        private String side; // BUY or SELL
        private BigDecimal price;
        private BigDecimal quantity;

        public Order(String orderId, String symbol, String side, BigDecimal price, BigDecimal quantity) {
            this.orderId = orderId;
            this.symbol = symbol;
            this.side = side;
            this.price = price;
            this.quantity = quantity;
        }

        @Override
        public String toString() {
            return "Order{" +
                    "orderId='" + orderId + '\'' +
                    ", symbol='" + symbol + '\'' +
                    ", side='" + side + '\'' +
                    ", price=" + price +
                    ", quantity=" + quantity +
                    '}';
        }
    }
}