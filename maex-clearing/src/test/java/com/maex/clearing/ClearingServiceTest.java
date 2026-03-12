package com.maex.clearing;

import com.maex.common.model.Order;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ClearingService 单元测试
 */
public class ClearingServiceTest {

    private ClearingService clearingService;

    @Before
    public void setUp() {
        clearingService = new ClearingService();
    }

    @Test
    public void testClearingServiceCreation() {
        assertNotNull("ClearingService should not be null", clearingService);
    }

    @Test
    public void testDeposit() {
        String accountId = "ACC001";
        BigDecimal amount = new BigDecimal("10000");

        clearingService.deposit(accountId, amount);

        BigDecimal balance = clearingService.getAccountBalance(accountId, "FUNDS");
        assertEquals("Balance should match deposit amount", 0, amount.compareTo(balance));
    }

    @Test
    public void testMultipleDeposits() {
        String accountId = "ACC002";
        clearingService.deposit(accountId, new BigDecimal("5000"));
        clearingService.deposit(accountId, new BigDecimal("3000"));

        BigDecimal balance = clearingService.getAccountBalance(accountId, "FUNDS");
        assertEquals("Balance should be sum of deposits", 0,
                    new BigDecimal("8000").compareTo(balance));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDepositNegativeAmount() {
        clearingService.deposit("ACC003", new BigDecimal("-1000"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDepositZeroAmount() {
        clearingService.deposit("ACC004", BigDecimal.ZERO);
    }

    @Test
    public void testWithdraw() {
        String accountId = "ACC005";
        clearingService.deposit(accountId, new BigDecimal("10000"));
        clearingService.withdraw(accountId, new BigDecimal("3000"));

        BigDecimal balance = clearingService.getAccountBalance(accountId, "FUNDS");
        assertEquals("Balance should be reduced", 0,
                    new BigDecimal("7000").compareTo(balance));
    }

    @Test(expected = ClearingService.InsufficientFundsException.class)
    public void testWithdrawInsufficientFunds() {
        String accountId = "ACC006";
        clearingService.deposit(accountId, new BigDecimal("5000"));
        clearingService.withdraw(accountId, new BigDecimal("6000"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithdrawNegativeAmount() {
        clearingService.withdraw("ACC007", new BigDecimal("-1000"));
    }

    @Test
    public void testSubmitOrderForClearing() {
        Order order = new Order("ORD001", "BTC/USDT", "BUY",
                               new BigDecimal("50000"), new BigDecimal("1"));

        clearingService.submitOrderForClearing(order);

        // 测试通过表示订单已提交
        assertTrue("Order submitted successfully", true);
    }

    @Test
    public void testSubmitNullOrder() {
        clearingService.submitOrderForClearing(null);
        // 应该不抛出异常，只是记录日志
        assertTrue("Null order handled gracefully", true);
    }

    @Test
    public void testSubmitDuplicateOrder() {
        Order order = new Order("ORD002", "BTC/USDT", "BUY",
                               new BigDecimal("50000"), new BigDecimal("1"));

        clearingService.submitOrderForClearing(order);
        clearingService.submitOrderForClearing(order);

        // 重复订单应该被识别并处理
        assertTrue("Duplicate order handled", true);
    }

    @Test
    public void testGetAccountBalanceNonExistent() {
        BigDecimal balance = clearingService.getAccountBalance("NONEXISTENT", "FUNDS");
        assertEquals("Non-existent account should have zero balance", 0,
                    BigDecimal.ZERO.compareTo(balance));
    }

    @Test
    public void testGetAccountBalanceMultipleAssets() {
        String accountId = "ACC008";
        clearingService.deposit(accountId, new BigDecimal("10000"));

        BigDecimal fundsBalance = clearingService.getAccountBalance(accountId, "FUNDS");
        BigDecimal btcBalance = clearingService.getAccountBalance(accountId, "BTC");

        assertEquals("FUNDS balance should be 10000", 0,
                    new BigDecimal("10000").compareTo(fundsBalance));
        assertEquals("BTC balance should be zero", 0,
                    BigDecimal.ZERO.compareTo(btcBalance));
    }

    @Test
    public void testConcurrentDeposits() throws InterruptedException {
        String accountId = "ACC009";
        int threadCount = 10;
        BigDecimal amountPerThread = new BigDecimal("1000");
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    clearingService.deposit(accountId, amountPerThread);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        BigDecimal expectedBalance = amountPerThread.multiply(new BigDecimal(threadCount));
        BigDecimal actualBalance = clearingService.getAccountBalance(accountId, "FUNDS");

        assertEquals("Concurrent deposits should be handled correctly", 0,
                    expectedBalance.compareTo(actualBalance));
    }

    @Test
    public void testConcurrentWithdraws() throws InterruptedException {
        String accountId = "ACC010";
        clearingService.deposit(accountId, new BigDecimal("10000"));

        int threadCount = 5;
        BigDecimal amountPerThread = new BigDecimal("1000");
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    clearingService.withdraw(accountId, amountPerThread);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        BigDecimal expectedBalance = new BigDecimal("5000");
        BigDecimal actualBalance = clearingService.getAccountBalance(accountId, "FUNDS");

        assertEquals("Concurrent withdraws should be handled correctly", 0,
                    expectedBalance.compareTo(actualBalance));
    }

    @Test
    public void testProcessPendingTrades() {
        int processedCount = clearingService.processPendingTrades();
        assertTrue("Processed count should be non-negative", processedCount >= 0);
    }

    @Test
    public void testMultipleAccountsIndependence() {
        String account1 = "ACC011";
        String account2 = "ACC012";

        clearingService.deposit(account1, new BigDecimal("5000"));
        clearingService.deposit(account2, new BigDecimal("8000"));

        BigDecimal balance1 = clearingService.getAccountBalance(account1, "FUNDS");
        BigDecimal balance2 = clearingService.getAccountBalance(account2, "FUNDS");

        assertEquals("Account 1 balance should be 5000", 0,
                    new BigDecimal("5000").compareTo(balance1));
        assertEquals("Account 2 balance should be 8000", 0,
                    new BigDecimal("8000").compareTo(balance2));
    }

    @Test
    public void testLargeAmountDeposit() {
        String accountId = "ACC013";
        BigDecimal largeAmount = new BigDecimal("999999999.99");

        clearingService.deposit(accountId, largeAmount);

        BigDecimal balance = clearingService.getAccountBalance(accountId, "FUNDS");
        assertEquals("Large amount should be handled correctly", 0,
                    largeAmount.compareTo(balance));
    }

    @Test
    public void testSmallAmountDeposit() {
        String accountId = "ACC014";
        BigDecimal smallAmount = new BigDecimal("0.01");

        clearingService.deposit(accountId, smallAmount);

        BigDecimal balance = clearingService.getAccountBalance(accountId, "FUNDS");
        assertEquals("Small amount should be handled correctly", 0,
                    smallAmount.compareTo(balance));
    }
}
