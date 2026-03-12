package com.maex.common.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 订单实体类 - 定义订单的基本属性
 */
@Getter
@Setter
public class Order implements Serializable {
    private static final long serialVersionUID = 1L;

    private String orderId;
    private String symbol;
    private String side; // BUY or SELL
    private BigDecimal price;
    private BigDecimal quantity;
    private LocalDateTime timestamp;

    // 构造函数
    public Order() {
    }

    public Order(String orderId, String symbol, String side, 
                BigDecimal price, BigDecimal quantity) {
        this.orderId = orderId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", side='" + side + '\'' +
                ", price=" + price +
                ", quantity=" + quantity +
                ", timestamp=" + timestamp +
                '}';
    }
}