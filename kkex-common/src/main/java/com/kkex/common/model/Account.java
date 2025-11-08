package com.kkex.common.model;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户资金账户类
 * 表示用户的资金账户信息
 */
@Getter
@Setter
public class Account {
    
    /**
     * 账户ID
     */
    private String accountId;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 账户余额
     */
    private BigDecimal balance;
    
    /**
     * 可用余额
     */
    private BigDecimal availableBalance;
    
    /**
     * 冻结余额
     */
    private BigDecimal frozenBalance;
    
    /**
     * 货币类型
     */
    private String currency;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 无参构造函数
     */
    public Account() {
        this.balance = BigDecimal.ZERO;
        this.availableBalance = BigDecimal.ZERO;
        this.frozenBalance = BigDecimal.ZERO;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 带参构造函数
     */
    public Account(String accountId, String userId, String currency) {
        this();
        this.accountId = accountId;
        this.userId = userId;
        this.currency = currency;
    }
    
    @Override
    public String toString() {
        return "Account{" +
                "accountId='" + accountId + '\'' +
                ", userId='" + userId + '\'' +
                ", balance=" + balance +
                ", availableBalance=" + availableBalance +
                ", frozenBalance=" + frozenBalance +
                ", currency='" + currency + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}