
package dev.akira.payflow.ledger.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class LedgerDatabaseConstraintsIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void commitsBalancedTransaction() {
        UUID debitAccount = createAccount("BRL");
        UUID creditAccount = createAccount("BRL");

        assertDoesNotThrow(() ->
            inTransactionWithoutResult(() -> {
                UUID transactionId = insertTransaction("TRANSFER", null);

                insertEntry(transactionId, debitAccount, "DEBIT", 10_000);
                insertEntry(transactionId, creditAccount, "CREDIT", 10_000);
            })
        );
    }

    @Test
    void rejectsUnbalancedTransactionAtCommit() {
        UUID debitAccount = createAccount("BRL");
        UUID creditAccount = createAccount("BRL");

        assertThrows(RuntimeException.class, () ->
            inTransactionWithoutResult(() -> {
                UUID transactionId = insertTransaction("TRANSFER", null);

                insertEntry(transactionId, debitAccount, "DEBIT", 10_000);
                insertEntry(transactionId, creditAccount, "CREDIT", 9_000);
            })
        );
    }

    @Test
    void rejectsTransactionWithFewerThanTwoEntriesAtCommit() {
        UUID debitAccount = createAccount("BRL");

        assertThrows(RuntimeException.class, () ->
            inTransactionWithoutResult(() -> {
                UUID transactionId = insertTransaction("TRANSFER", null);

                insertEntry(transactionId, debitAccount, "DEBIT", 10_000);
            })
        );
    }

    @Test
    void rejectsZeroAmount() {
        UUID debitAccount = createAccount("BRL");
        UUID creditAccount = createAccount("BRL");

        assertThrows(DataAccessException.class, () ->
            inTransactionWithoutResult(() -> {
                UUID transactionId = insertTransaction("TRANSFER", null);

                insertEntry(transactionId, debitAccount, "DEBIT", 0);
                insertEntry(transactionId, creditAccount, "CREDIT", 10_000);
            })
        );
    }

    @Test
    void rejectsEntryForNonexistentAccount() {
        UUID debitAccount = createAccount("BRL");
        UUID nonexistentAccountId = UUID.randomUUID();

        assertThrows(DataAccessException.class, () ->
            inTransactionWithoutResult(() -> {
                UUID transactionId = insertTransaction("TRANSFER", null);

                insertEntry(transactionId, debitAccount, "DEBIT", 10_000);
                insertEntry(
                    transactionId,
                    nonexistentAccountId,
                    "CREDIT",
                    10_000
                );
            })
        );
    }

    @Test
    void rejectsUpdatingCommittedLedgerTransaction() {
        UUID debitAccount = createAccount("BRL");
        UUID creditAccount = createAccount("BRL");

        UUID transactionId = createBalancedTransaction(
            debitAccount,
            creditAccount
        );

        assertThrows(DataAccessException.class, () ->
            jdbcTemplate.update(
                """
                UPDATE ledger_transactions
                SET reference = ?
                WHERE id = ?
                """,
                "modified-reference",
                transactionId
            )
        );
    }

    @Test
    void rejectsDeletingCommittedLedgerEntry() {
        UUID debitAccount = createAccount("BRL");
        UUID creditAccount = createAccount("BRL");

        UUID transactionId = createBalancedTransaction(
            debitAccount,
            creditAccount
        );

        UUID entryId = jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM ledger_entries
            WHERE transaction_id = ?
              AND entry_type = 'DEBIT'
            """,
            UUID.class,
            transactionId
        );

        assertThrows(DataAccessException.class, () ->
            jdbcTemplate.update(
                "DELETE FROM ledger_entries WHERE id = ?",
                entryId
            )
        );
    }

    @Test
    void rejectsChangingAccountCurrency() {
        UUID accountId = createAccount("BRL");

        assertThrows(DataAccessException.class, () ->
            jdbcTemplate.update(
                """
                UPDATE accounts
                SET currency_code = ?
                WHERE id = ?
                """,
                "USD",
                accountId
            )
        );
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

    private UUID createBalancedTransaction(
        UUID debitAccount,
        UUID creditAccount
    ) {
        return inTransaction(status -> {
            UUID transactionId = insertTransaction("TRANSFER", null);

            insertEntry(transactionId, debitAccount, "DEBIT", 10_000);
            insertEntry(transactionId, creditAccount, "CREDIT", 10_000);

            return transactionId;
        });
    }

    private UUID insertTransaction(
        String transactionType,
        UUID reversesTransactionId
    ) {
        UUID transactionId = UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO ledger_transactions (
                id,
                transaction_type,
                reverses_transaction_id
            )
            VALUES (?, ?, ?)
            """,
            transactionId,
            transactionType,
            reversesTransactionId
        );

        return transactionId;
    }

    private void insertEntry(
        UUID transactionId,
        UUID accountId,
        String entryType,
        long amountMinor
    ) {
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

    private <T> T inTransaction(TransactionCallback<T> callback) {
        return new TransactionTemplate(transactionManager)
            .execute(callback);
    }

    private void inTransactionWithoutResult(Runnable action) {
        new TransactionTemplate(transactionManager)
            .executeWithoutResult(status -> action.run());
    }
}
