package dev.akira.payflow.transfer.application.service;

import dev.akira.payflow.account.application.port.out.AccountBalanceQuery;
import dev.akira.payflow.account.domain.AccountBalance;
import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.common.domain.money.CurrencyCode;
import dev.akira.payflow.common.domain.money.Money;
import dev.akira.payflow.common.error.BusinessException;
import dev.akira.payflow.common.error.ErrorCode;
import dev.akira.payflow.ledger.domain.EntryType;
import dev.akira.payflow.ledger.domain.LedgerTransaction;
import dev.akira.payflow.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class TransferIdempotencyIntregationTest extends AbstractPostgresIntegrationTest {

    private static final CurrencyCode BRL = CurrencyCode.of("BRL");
    private static final long WAIT_SECONDS = 10;

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountBalanceQuery accountBalanceQuery;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void repeatingSameRequestReturnsOriginalTransactionWithoutDuplicateEntries() {
        TestAccounts accounts = createAccounts();
        postOpeningBalance(accounts.sourceAccountId(), accounts.fundingAccountId(), 10_000);

        String key = "idempotent-repeat-" + UUID.randomUUID();
        TransferCommand command = command(
                accounts.sourceAccountId(), accounts.destinationAId(), 2_500, key
        );

        LedgerTransaction first = transferService.execute(command);
        LedgerTransaction second = transferService.execute(command);

        assertEquals(first.id(), second.id());
        assertEquals(new AccountBalance(7_500, BRL), balanceOf(accounts.sourceAccountId));
        assertEquals(new AccountBalance(2_500, BRL), balanceOf(accounts.destinationAId));
        assertEquals(1, countTransferTransactions(accounts.sourceAccountId()));
        assertEquals(2, countTransferEntries(accounts.sourceAccountId()));
        assertEquals(1, countIdempotencyRecords(accounts.sourceHolderId(), key));
    }

    @Test
    void reusingKeyWithDifferentAmountIsRejectedWithoutCreatingAnotherTransfer() {
        TestAccounts accounts = createAccounts();
        postOpeningBalance(accounts.sourceAccountId(), accounts.fundingAccountId(), 10_000);
        String key = "idempotent-amount-" + UUID.randomUUID();

        transferService.execute(command(accounts.sourceAccountId(), accounts.destinationAId(), 1_000, key));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transferService.execute(command(accounts.sourceAccountId(), accounts.destinationAId(), 2_000, key))
        );

        assertEquals(ErrorCode.IDEMPOTENCY_KEY_REUSED, exception.errorCode());
        assertEquals(new AccountBalance(9_000, BRL), balanceOf(accounts.sourceAccountId()));
        assertEquals(new AccountBalance(1_000, BRL), balanceOf(accounts.destinationAId()));
        assertEquals(1, countTransferTransactions(accounts.sourceAccountId()));
        assertEquals(2,  countTransferEntries(accounts.sourceAccountId()));
        assertEquals(1, countIdempotencyRecords(accounts.sourceHolderId(), key));
    }

    @Test
    void reusingKeyWithDifferentDestinationIsRejected() {
        TestAccounts accounts = createAccounts();
        postOpeningBalance(accounts.sourceAccountId(), accounts.fundingAccountId(), 10_000);
        String key = "idempotent-destination-"  + UUID.randomUUID();

        transferService.execute(command(accounts.sourceAccountId(), accounts.destinationAId(), 1_000, key));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transferService.execute(command(accounts.sourceAccountId(), accounts.destinationBId(), 1_000, key))
        );

        assertEquals(ErrorCode.IDEMPOTENCY_KEY_REUSED, exception.errorCode());
        assertEquals(new AccountBalance(9_000, BRL), balanceOf(accounts.sourceAccountId()));
        assertEquals(new AccountBalance(1_000, BRL), balanceOf(accounts.destinationAId()));
        assertEquals(0, balanceOfOrZero(accounts.destinationBId()).minorUnits());
        assertEquals(1, countTransferTransactions(accounts.sourceAccountId()));
        assertEquals(2,   countTransferEntries(accounts.sourceAccountId()));
        assertEquals(1, countIdempotencyRecords(accounts.sourceHolderId(), key));
    }

    @Test
    void concurrentRequestWithSameKeyAndDifferentPayloadPersistOnlyOneTransfer() throws Exception {
        TestAccounts accounts =  createAccounts();
        postOpeningBalance(accounts.sourceAccountId(), accounts.fundingAccountId(), 10_000);

        String key = "idempotent-race-" + UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<TransferOutcome> first = executor.submit(() -> {
                awaitStart(start);
                return attemptTransfer(accounts.sourceAccountId(), accounts.destinationAId(), 1_000, key);
            });

            Future<TransferOutcome> second = executor.submit(() -> {
               awaitStart(start);
               return attemptTransfer(accounts.sourceAccountId(), accounts.destinationAId(), 2_000, key);
            });

            start.countDown();

            TransferOutcome firstResult = first.get(WAIT_SECONDS, TimeUnit.SECONDS);
            TransferOutcome secondResult = second.get(WAIT_SECONDS, TimeUnit.SECONDS);

            assertEquals(1, countSuccessful(firstResult, secondResult), "Exactly one request payload should succeed");
            assertEquals(1, countIdempotencyKeyReused(firstResult, secondResult), "The competing payload should be rejected as key reuse");

            long winningAmount = firstResult.succeeded() ? 1000 : 2000;

            assertEquals(new AccountBalance(10_000 - winningAmount, BRL), balanceOf(accounts.sourceAccountId()));
            assertEquals(new AccountBalance(winningAmount, BRL), balanceOf(accounts.destinationAId()));
            assertEquals(1, countTransferTransactions(accounts.sourceAccountId()));
            assertEquals(2, countTransferEntries(accounts.sourceAccountId()));
            assertEquals(1, countIdempotencyRecords(accounts.sourceHolderId(), key));
        } finally {
            start.countDown();
            stopExecutor(executor);
        }
    }

    @Test
    void insufficientFundsRollsBackLedgerAndIdempotencyRecord() {
        TestAccounts accounts = createAccounts();
        postOpeningBalance(accounts.sourceAccountId(), accounts.fundingAccountId(), 1_000);
        String key = "idempotent-rollback-" + UUID.randomUUID();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transferService.execute(command(accounts.sourceAccountId(), accounts.destinationAId(), 2_000, key))
        );

        assertEquals(ErrorCode.INSUFFICIENT_FUNDS, exception.errorCode());
        assertEquals(new AccountBalance(1_000, BRL), balanceOf(accounts.sourceAccountId()));
        assertEquals(0, countTransferTransactions(accounts.sourceAccountId()));
        assertEquals(0, countTransferEntries(accounts.sourceAccountId()));
        assertEquals(0, countIdempotencyRecords(accounts.sourceHolderId(), key));
    }

    private TransferOutcome attemptTransfer(AccountId source, AccountId destination, long amountMinor, String key) {
        try {
            return new TransferOutcome(
                    transferService.execute(command(source, destination, amountMinor, key)),
                    null
            );
        } catch (BusinessException exception) {
            return new TransferOutcome(null, exception.errorCode());
        }
    }

    private TransferCommand command(AccountId source, AccountId destination, long amountMinor, String key) {
        return new TransferCommand(
                source, destination, Money.ofMinorUnits(amountMinor, BRL), key
        );
    }

    private AccountBalance balanceOf(AccountId accountId) {
        return accountBalanceQuery.findByAccountId(accountId)
                .orElseThrow(() -> new AssertionError("Missing balance for account " + accountId.value()));
    }

    private AccountBalance balanceOfOrZero(AccountId accountId) {
        return accountBalanceQuery.findByAccountId(accountId)
                .orElse(new AccountBalance(0, BRL));
    }

    private TestAccounts createAccounts() {
        UUID sourceHolderId = createHolder();
        UUID destinationAHolderId = createHolder();
        UUID destinationBHolderId = createHolder();
        UUID fundingHolderId = createHolder();

        return new TestAccounts(
                sourceHolderId,
                new AccountId(createAccount(sourceHolderId)),
                new AccountId(createAccount(destinationAHolderId)),
                new AccountId(createAccount(destinationBHolderId)),
                new AccountId(createAccount(fundingHolderId))
        );
    }

    private UUID createHolder() {
        UUID holderId = UUID.randomUUID();
        String displayName = "T" + UUID.randomUUID()
                .toString().replace("-", "").substring(0, 14);

        jdbcTemplate.update(
            """
                INSERT INTO account_holders (id, holder_type, display_name, status)
                VALUES (?, 'INDIVIDUAL', ?, 'ACTIVE')
                """,
                holderId,
                displayName
        );

        return holderId;
    }

    private UUID createAccount(UUID holderId) {
        UUID accountId = UUID.randomUUID();
        String accountNumber = "PF" + UUID.randomUUID().toString().replace("-", "");

        jdbcTemplate.update(
        """
            INSERT INTO accounts (
                id, holder_id, account_number,
                account_type, currency_code, status
            )
            VALUES (?, ?, ?, 'CUSTOMER', 'BRL', 'ACTIVE')
            """,
            accountId,
            holderId,
            accountNumber
        );

        return accountId;
    }

    private void postOpeningBalance(AccountId creditedAccountId, AccountId counterpartyAccountId, long amountMinorUnits) {
        UUID transactionId = UUID.randomUUID();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update(
                    """
                        INSERT INTO ledger_transactions (id, transaction_type, reference)
                        VALUES (?, 'ADJUSTMENT', ?)
                        """,
                    transactionId,
                    "TEST_OPENING_BALANCE"
            );

            insertEntry(transactionId, creditedAccountId, EntryType.CREDIT, amountMinorUnits);
            insertEntry(transactionId, counterpartyAccountId, EntryType.DEBIT, amountMinorUnits);
        });
    }

    private void insertEntry(UUID transactionId, AccountId accountId, EntryType entryType, long amountMinor) {
        jdbcTemplate.update(
        """
            INSERT INTO ledger_entries (
                transaction_id, account_id, entry_type, amount_minor
            )
            VALUES (?, ?, ?, ?)
            """,
            transactionId,
            accountId.value(),
            entryType.name(),
            amountMinor
        );
    }

    private int countTransferTransactions(AccountId debitAccountId) {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(DISTINCT t.id)
            FROM ledger_transactions t
            JOIN ledger_entries e ON e.transaction_id = t.id
            WHERE t.transaction_type = 'TRANSFER'
                AND t.reference = 'INTERNAL_TRANSFER'
                AND e.account_id = ?
                AND e.entry_type = 'DEBIT'
            """,
            Integer.class,
            debitAccountId.value()
        );
    }

    private int countTransferEntries(AccountId debitAccountId) {
        return jdbcTemplate.queryForObject(
        """
            SELECT COUNT(*)
            FROM ledger_entries e
            JOIN ledger_transactions t ON t.id = transaction_id
            WHERE t.transaction_type = 'TRANSFER'
                AND t.reference = 'INTERNAL_TRANSFER'
                AND t.id IN (
                    SELECT source_entry.transaction_id
                    FROM ledger_entries source_entry
                    WHERE source_entry.account_id = ?
                        AND source_entry.entry_type = 'DEBIT'
                )
            """,
            Integer.class,
            debitAccountId.value()
        );
    }

    private int countIdempotencyRecords(UUID holderId, String key) {
        return jdbcTemplate.queryForObject(
        """
            SELECT COUNT(*)
            FROM idempotency_requests
            WHERE holder_id = ?
                AND operation_type = 'TRANSFER'
                AND idempotency_key = ?
            """,
            Integer.class,
            holderId,
            key
        );
    }

    private long countSuccessful(TransferOutcome first, TransferOutcome second) {
        return (first.succeeded() ? 1 : 0) + (second.succeeded() ? 1 : 0);
    }

    private long countIdempotencyKeyReused(TransferOutcome first, TransferOutcome second) {
        return (first.errorCode() == ErrorCode.IDEMPOTENCY_KEY_REUSED ? 1 : 0)
                + (second.errorCode() == ErrorCode.IDEMPOTENCY_KEY_REUSED ? 1 : 0);
    }

    private void awaitStart(CountDownLatch start) throws InterruptedException {
        if (!start.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out waiting for concurrent start");
        }
    }

    private void stopExecutor(ExecutorService executor) throws InterruptedException {
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Worker threads should terminate");
    }

    private record TestAccounts(
            UUID sourceHolderId,
            AccountId sourceAccountId,
            AccountId destinationAId,
            AccountId destinationBId,
            AccountId fundingAccountId
    ) {}

    private record TransferOutcome(LedgerTransaction transaction, ErrorCode errorCode) {
        boolean succeeded() {
            return transaction != null;
        }
    }
}
