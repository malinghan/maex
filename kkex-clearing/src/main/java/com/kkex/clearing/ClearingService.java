package com.kkex.clearing;

import com.kkex.common.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 清算服务 - 负责交易的清算和结算处理
 */
public class ClearingService {
    private static final Logger logger = LoggerFactory.getLogger(ClearingService.class);
    
    // 账户余额映射
    private final Map<String, Map<String, BigDecimal>> accountBalances = new ConcurrentHashMap<>();
    // 待清算的交易
    private final Queue<Trade> pendingTrades = new LinkedList<>();
    // 已清算的交易
    private final Set<String> clearedTrades = ConcurrentHashMap.newKeySet();

    public ClearingService() {
        logger.info("Clearing Service started");
    }

    /**
     * 提交交易进行清算
     * @param trade 待清算的交易
     */
    public synchronized void submitTradeForClearing(Trade trade) {
        if (trade == null) {
            logger.error("Cannot submit null trade for clearing");
            return;
        }
        
        if (clearedTrades.contains(trade.getTradeId())) {
            logger.warn("Trade already cleared: {}", trade.getTradeId());
            return;
        }
        
        pendingTrades.offer(trade);
        logger.debug("Trade submitted for clearing: {}", trade.getTradeId());
    }

    /**
     * 处理所有待清算的交易
     * @return 成功清算的交易数量
     */
    public synchronized int processPendingTrades() {
        int processedCount = 0;
        Trade trade;
        
        while ((trade = pendingTrades.poll()) != null) {
            boolean cleared = clearTrade(trade);
            if (cleared) {
                processedCount++;
                clearedTrades.add(trade.getTradeId());
                logger.info("Trade cleared successfully: {}", trade.getTradeId());
            } else {
                logger.error("Failed to clear trade: {}", trade.getTradeId());
                // 可以选择将失败的交易重新放入队列或进行其他处理
            }
        }
        
        return processedCount;
    }

    /**
     * 清算单个交易
     * @param trade 待清算的交易
     * @return 清算是否成功
     */
    private boolean clearTrade(Trade trade) {
        try {
            String symbol = trade.getSymbol();
            BigDecimal price = trade.getPrice();
            BigDecimal quantity = trade.getQuantity();
            String orderId = trade.getOrderId();
            String side = trade.getSide();
            
            // 获取或创建买家和卖家的账户余额
            Map<String, BigDecimal> buyerBalances = getOrCreateAccountBalances(orderId);
            
            // 计算交易金额
            BigDecimal amount = price.multiply(quantity);
            
            // 执行清算逻辑
            // 在实际系统中，这里会涉及到资金扣划、证券过户等操作
            if (side.equals("BUY")) {
                // 买入交易：扣除资金，增加证券
                deductFunds(buyerBalances, amount);
                addAsset(buyerBalances, symbol, quantity);
            } else if (side.equals("SELL")) {
                // 卖出交易：扣除证券，增加资金
                deductAsset(buyerBalances, symbol, quantity);
                addFunds(buyerBalances, amount);
            }
            
            // 创建清算记录
            createClearingRecord(trade);
            
            return true;
        } catch (Exception e) {
            logger.error("Error clearing trade {}: {}", trade.getTradeId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取或创建账户余额
     * @param accountId 账户ID
     * @return 账户余额映射
     */
    private Map<String, BigDecimal> getOrCreateAccountBalances(String accountId) {
        return accountBalances.computeIfAbsent(accountId, k -> new ConcurrentHashMap<>());
    }

    /**
     * 扣除资金
     * @param balances 账户余额
     * @param amount 金额
     */
    private void deductFunds(Map<String, BigDecimal> balances, BigDecimal amount) {
        BigDecimal currentFunds = balances.getOrDefault("FUNDS", BigDecimal.ZERO);
        if (currentFunds.compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds for transaction");
        }
        balances.put("FUNDS", currentFunds.subtract(amount));
    }

    /**
     * 增加资金
     * @param balances 账户余额
     * @param amount 金额
     */
    private void addFunds(Map<String, BigDecimal> balances, BigDecimal amount) {
        BigDecimal currentFunds = balances.getOrDefault("FUNDS", BigDecimal.ZERO);
        balances.put("FUNDS", currentFunds.add(amount));
    }

    /**
     * 扣除资产
     * @param balances 账户余额
     * @param asset 资产类型
     * @param quantity 数量
     */
    private void deductAsset(Map<String, BigDecimal> balances, String asset, BigDecimal quantity) {
        BigDecimal currentAsset = balances.getOrDefault(asset, BigDecimal.ZERO);
        if (currentAsset.compareTo(quantity) < 0) {
            throw new InsufficientAssetsException("Insufficient assets for transaction");
        }
        balances.put(asset, currentAsset.subtract(quantity));
    }

    /**
     * 增加资产
     * @param balances 账户余额
     * @param asset 资产类型
     * @param quantity 数量
     */
    private void addAsset(Map<String, BigDecimal> balances, String asset, BigDecimal quantity) {
        BigDecimal currentAsset = balances.getOrDefault(asset, BigDecimal.ZERO);
        balances.put(asset, currentAsset.add(quantity));
    }

    /**
     * 创建清算记录
     * @param trade 交易信息
     */
    private void createClearingRecord(Trade trade) {
        // 在实际系统中，这里会将清算记录持久化到数据库
        logger.debug("Clearing record created for trade: {}", trade.getTradeId());
    }

    /**
     * 获取账户余额
     * @param accountId 账户ID
     * @param asset 资产类型
     * @return 账户余额
     */
    public BigDecimal getAccountBalance(String accountId, String asset) {
        Map<String, BigDecimal> balances = accountBalances.get(accountId);
        if (balances == null) {
            return BigDecimal.ZERO;
        }
        return balances.getOrDefault(asset, BigDecimal.ZERO);
    }

    /**
     * 存款
     * @param accountId 账户ID
     * @param amount 金额
     */
    public void deposit(String accountId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        Map<String, BigDecimal> balances = getOrCreateAccountBalances(accountId);
        addFunds(balances, amount);
        logger.info("Deposited {} to account {}", amount, accountId);
    }

    /**
     * 提现
     * @param accountId 账户ID
     * @param amount 金额
     */
    public void withdraw(String accountId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        Map<String, BigDecimal> balances = getOrCreateAccountBalances(accountId);
        deductFunds(balances, amount);
        logger.info("Withdrew {} from account {}", amount, accountId);
    }

    /**
     * 清算记录类 - 记录清算的详细信息
     */
    public static class ClearingRecord {
        private String clearingId;
        private String tradeId;
        private String orderId;
        private LocalDateTime clearingTime;
        private String status;
        private String notes;

        public ClearingRecord(String tradeId, String orderId) {
            this.clearingId = "CLR" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
            this.tradeId = tradeId;
            this.orderId = orderId;
            this.clearingTime = LocalDateTime.now();
            this.status = "PENDING";
        }

        // Getters and Setters
        public String getClearingId() {
            return clearingId;
        }

        public String getTradeId() {
            return tradeId;
        }

        public String getOrderId() {
            return orderId;
        }

        public LocalDateTime getClearingTime() {
            return clearingTime;
        }

        public void setClearingTime(LocalDateTime clearingTime) {
            this.clearingTime = clearingTime;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }

        @Override
        public String toString() {
            return "ClearingRecord{" +
                    "clearingId='" + clearingId + '\'' +
                    ", tradeId='" + tradeId + '\'' +
                    ", orderId='" + orderId + '\'' +
                    ", clearingTime=" + clearingTime +
                    ", status='" + status + '\'' +
                    ", notes='" + notes + '\'' +
                    '}';
        }
    }

    /**
     * 资金不足异常
     */
    public static class InsufficientFundsException extends RuntimeException {
        public InsufficientFundsException(String message) {
            super(message);
        }
    }

    /**
     * 资产不足异常
     */
    public static class InsufficientAssetsException extends RuntimeException {
        public InsufficientAssetsException(String message) {
            super(message);
        }
    }
}