package com.maex.common.model;

import org.junit.Test;
import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Account 类单元测试
 */
public class AccountTest {

    @Test
    public void testAccountCreation() {
        Account account = new Account();

        assertNotNull("Account should not be null", account);
        assertEquals("Balance should be zero", 0,
                    BigDecimal.ZERO.compareTo(account.getBalance()));
        assertEquals("Available balance should be zero", 0,
                    BigDecimal.ZERO.compareTo(account.getAvailableBalance()));
        assertEquals("Frozen balance should be zero", 0,
                    BigDecimal.ZERO.compareTo(account.getFrozenBalance()));
        assertNotNull("Created time should be set", account.getCreatedAt());
        assertNotNull("Updated time should be set", account.getUpdatedAt());
    }

    @Test
    public void testAccountWithParameters() {
        String accountId = "ACC001";
        String userId = "USER001";
        String currency = "USDT";

        Account account = new Account(accountId, userId, currency);

        assertEquals("Account ID should match", accountId, account.getAccountId());
        assertEquals("User ID should match", userId, account.getUserId());
        assertEquals("Currency should match", currency, account.getCurrency());
        assertEquals("Balance should be zero", 0,
                    BigDecimal.ZERO.compareTo(account.getBalance()));
    }

    @Test
    public void testAccountSettersAndGetters() {
        Account account = new Account();

        account.setAccountId("ACC002");
        account.setUserId("USER002");
        account.setBalance(new BigDecimal("10000"));
        account.setAvailableBalance(new BigDecimal("8000"));
        account.setFrozenBalance(new BigDecimal("2000"));
        account.setCurrency("BTC");
        LocalDateTime now = LocalDateTime.now();
        account.setCreatedAt(now);
        account.setUpdatedAt(now);

        assertEquals("ACC002", account.getAccountId());
        assertEquals("USER002", account.getUserId());
        assertEquals(0, new BigDecimal("10000").compareTo(account.getBalance()));
        assertEquals(0, new BigDecimal("8000").compareTo(account.getAvailableBalance()));
        assertEquals(0, new BigDecimal("2000").compareTo(account.getFrozenBalance()));
        assertEquals("BTC", account.getCurrency());
        assertEquals(now, account.getCreatedAt());
        assertEquals(now, account.getUpdatedAt());
    }

    @Test
    public void testAccountBalanceConsistency() {
        Account account = new Account("ACC003", "USER003", "USDT");

        BigDecimal available = new BigDecimal("7000");
        BigDecimal frozen = new BigDecimal("3000");
        BigDecimal total = available.add(frozen);

        account.setAvailableBalance(available);
        account.setFrozenBalance(frozen);
        account.setBalance(total);

        assertEquals("Total balance should equal available + frozen", 0,
                    total.compareTo(account.getBalance()));
    }

    @Test
    public void testAccountToString() {
        Account account = new Account("ACC004", "USER004", "USDT");
        account.setBalance(new BigDecimal("5000"));

        String accountString = account.toString();

        assertTrue("Should contain accountId", accountString.contains("ACC004"));
        assertTrue("Should contain userId", accountString.contains("USER004"));
        assertTrue("Should contain currency", accountString.contains("USDT"));
    }

    @Test
    public void testAccountTimestampAutoGeneration() {
        LocalDateTime before = LocalDateTime.now();
        Account account = new Account("ACC005", "USER005", "BTC");
        LocalDateTime after = LocalDateTime.now();

        assertNotNull("Created time should be generated", account.getCreatedAt());
        assertNotNull("Updated time should be generated", account.getUpdatedAt());
        assertTrue("Created time should be after or equal to before time",
                  !account.getCreatedAt().isBefore(before));
        assertTrue("Created time should be before or equal to after time",
                  !account.getCreatedAt().isAfter(after));
    }

    @Test
    public void testAccountWithMultipleCurrencies() {
        Account usdtAccount = new Account("ACC006", "USER006", "USDT");
        Account btcAccount = new Account("ACC007", "USER006", "BTC");

        usdtAccount.setBalance(new BigDecimal("10000"));
        btcAccount.setBalance(new BigDecimal("1.5"));

        assertEquals("USDT", usdtAccount.getCurrency());
        assertEquals("BTC", btcAccount.getCurrency());
        assertEquals("USER006", usdtAccount.getUserId());
        assertEquals("USER006", btcAccount.getUserId());
    }
}
