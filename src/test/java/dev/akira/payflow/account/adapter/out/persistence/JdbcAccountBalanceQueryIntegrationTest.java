package dev.akira.payflow.account.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.akira.payflow.account.application.port.out.AccountBalanceQuery;
import dev.akira.payflow.account.domain.AccountBalance;
import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.common.domain.money.CurrencyCode;

@SpringBootTest
class JdbcAccountBalanceQueryIntegrationTest {
    
    @Autowired
    private AccountBalanceQuery accountBalanceQuery;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void returnsZeroForAccountWithoutLedgerEntries() {
        UUID accountId = createAccount("BRL");

        assertEquals(
            Optional.of(new AccountBalance(0, CurrencyCode.of("BRL"))),
            accountBalanceQuery.findByAccountId(new AccountId(accountId))
        );
    }

    @Test
    void creditsIncreaseTheAccountBalance() {
        UUID accountId = createAccount("BRL");
        UUID counterpartyAccountId = createAccount("BRL");

        postBalancedEntry(accountId, counterpartyAccountId, "CREDIT", 10_000);

        assertEquals(
            Optional.of(new AccountBalance(10_000, CurrencyCode.of("BRL"))),
            accountBalanceQuery.findByAccountId(new AccountId(accountId))
        );
    }

    @Test
    void debitsDecreaseTheAccountBalance() {
        UUID accountId = createAccount("BRL");
        UUID counterpartyAccountId = createAccount("BRL");

        postBalancedEntry(accountId, counterpartyAccountId, "DEBIT", 3000);

        assertEquals(
            Optional.of(new AccountBalance(-3_000, CurrencyCode.of("BRL"))),
            accountBalanceQuery.findByAccountId(new AccountId(accountId))
        );
    }

    @Test
    void calculatesNetBalanceFromCreditsAndDebits() {
        UUID accountId = createAccount("BRL");
        UUID firstCounterpartyId = createAccount("BRL");
        UUID secondCounterpartyId = createAccount("BRL");

        postBalancedEntry(accountId, firstCounterpartyId, "CREDIT", 10_000);
        postBalancedEntry(accountId, secondCounterpartyId, "DEBIT", 3_000);

        assertEquals(
            Optional.of(new AccountBalance(7_000, CurrencyCode.of("BRL"))),
            accountBalanceQuery.findByAccountId(new AccountId(accountId))
        );
    }

    @Test
    void returnsEmptyWhenAccountDoesNotExist() {
        Optional<AccountBalance> result = accountBalanceQuery.findByAccountId(new AccountId(UUID.randomUUID()));

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsTheAccountCurrency() {
        UUID accountId = createAccount("USD");

        AccountBalance balance = accountBalanceQuery
            .findByAccountId(new AccountId(accountId))
            .orElseThrow();

        assertEquals(CurrencyCode.of("USD"), balance.currency());
        assertEquals(0, balance.minorUnits());
    }

    private UUID createAccount(String currencyCode) {
        UUID holderId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        String displayName = "T" + UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 14);

        String accountNumber = "PF"
            + UUID.randomUUID().toString().replace("-", "");

        jdbcTemplate.update(
            """
            INSERT INTO account_holders (
                id,
                holder_type,
                display_name,
                status
            )
            VALUES (?, 'INDIVIDUAL', ?, 'ACTIVE')
            """,
            holderId,
            displayName
        );

        jdbcTemplate.update(
            """
            INSERT INTO accounts (
                id,
                holder_id,
                account_number,
                account_type,
                currency_code,
                status
            )
            VALUES (?, ?, ?, 'CUSTOMER', ?, 'ACTIVE')
            """,
            accountId,
            holderId,
            accountNumber,
            currencyCode
        );

        return accountId;
    }

    private void postBalancedEntry(UUID accountId, UUID counterpartyAccountId, String targetEntryType, long amountMinor) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID transactionId = UUID.randomUUID();

            jdbcTemplate.update(
                """
                INSERT INTO ledger_transactions (
                    id,
                    transaction_type,
                    reference
                )
                VALUES (?, 'ADJUSTMENT', ?)
                """,
                transactionId,
                "balance-test-" + UUID.randomUUID()
            );

            String counterpartyEntryType =
                "CREDIT".equals(targetEntryType) ? "DEBIT" : "CREDIT";

            insertEntry(transactionId, accountId, targetEntryType, amountMinor);
            insertEntry(transactionId, counterpartyAccountId, counterpartyEntryType, amountMinor);
        });
    }

    private void insertEntry(UUID transactionId, UUID accountId, String entryType, long amountMinor) {
        jdbcTemplate.update(
            """
            INSERT INTO ledger_entries (
                transaction_id,
                account_id,
                entry_type,
                amount_minor
            )
            VALUES (?, ?, ?, ?)
            """,
            transactionId,
            accountId,
            entryType,
            amountMinor
        );
    }
}
