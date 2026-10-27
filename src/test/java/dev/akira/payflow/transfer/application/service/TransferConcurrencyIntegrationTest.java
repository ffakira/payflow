package dev.akira.payflow.transfer.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import dev.akira.payflow.common.domain.money.Money;
import dev.akira.payflow.common.error.BusinessException;
import dev.akira.payflow.common.error.ErrorCode;
import dev.akira.payflow.ledger.domain.EntryType;
import dev.akira.payflow.ledger.domain.LedgerTransaction;
import dev.akira.payflow.support.AbstractPostgresIntegrationTest;

@SpringBootTest
class TransferConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final CurrencyCode BRL = CurrencyCode.of("BRL");

    private static final long STARTING_BALANCE = 10_000;
    private static final long TRANSFER_AMOUNT = 7_000;
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
    void concurrentTransfersCannotOverdrawSourceAccount() throws Exception {
        TestAccounts accounts = createAccounts();

        postOpeningBalance(
                accounts.sourceAccountId(),
                accounts.fundingAccountId(),
                STARTING_BALANCE
        );

        String firstKey = "overspend-a-" + UUID.randomUUID();
        String secondKey = "overspend-b-" + UUID.randomUUID();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<TransferOutcome> first = executor.submit(() -> {
                awaitStart(start);
                return attemptTransfer(
                        accounts.sourceAccountId(),
                        accounts.destinationAId(),
                        TRANSFER_AMOUNT,
                        firstKey
                );
            });

            Future<TransferOutcome> second = executor.submit(() -> {
                awaitStart(start);
                return attemptTransfer(
                        accounts.sourceAccountId(),
                        accounts.destinationBId(),
                        TRANSFER_AMOUNT,
                        secondKey
                );
            });

            start.countDown();

            TransferOutcome firstOutcome =
                    first.get(WAIT_SECONDS, TimeUnit.SECONDS);
            TransferOutcome secondOutcome =
                    second.get(WAIT_SECONDS, TimeUnit.SECONDS);

            assertEquals(
                    1,
                    countSuccessful(firstOutcome, secondOutcome),
                    "Exactly one transfer should succeed"
            );

            assertEquals(
                    1,
                    countInsufficientFunds(firstOutcome, secondOutcome),
                    "The losing transfer should fail for insufficient funds"
            );

            assertEquals(
                    new AccountBalance(3_000, BRL),
                    balanceOf(accounts.sourceAccountId())
            );

            long destinationABalance =
                    balanceOf(accounts.destinationAId()).minorUnits();
            long destinationBBalance =
                    balanceOf(accounts.destinationBId()).minorUnits();

            assertTrue(
                    (destinationABalance == 7_000 && destinationBBalance == 0)
                            || (destinationABalance == 0 && destinationBBalance == 7_000),
                    "Only one destination should receive the transfer"
            );

            assertEquals(
                    1,
                    countTransferTransactions(accounts.sourceAccountId()),
                    "Only one transfer transaction should debit this source"
            );

            assertEquals(
                    2,
                    countTransferEntries(accounts.sourceAccountId()),
                    "The successful transfer should have one debit and one credit"
            );

            assertEquals(
                    1,
                    countIdempotencyRecords(
                            accounts.sourceHolderId(),
                            firstKey,
                            secondKey
                    ),
                    "Only the successful request should have an idempotency record"
            );
        } finally {
            start.countDown();
            stopExecutor(executor);
        }
    }

    @Test
    void concurrentIdenticalRequestsCreateOnlyOneTransfer() throws Exception {
        TestAccounts accounts = createAccounts();

        postOpeningBalance(
                accounts.sourceAccountId(),
                accounts.fundingAccountId(),
                STARTING_BALANCE
        );

        String idempotencyKey = "same-request-" + UUID.randomUUID();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<LedgerTransaction> first = executor.submit(() -> {
                awaitStart(start);
                return transferService.execute(command(
                        accounts.sourceAccountId(),
                        accounts.destinationAId(),
                        2_500,
                        idempotencyKey
                ));
            });

            Future<LedgerTransaction> second = executor.submit(() -> {
                awaitStart(start);
                return transferService.execute(command(
                        accounts.sourceAccountId(),
                        accounts.destinationAId(),
                        2_500,
                        idempotencyKey
                ));
            });

            start.countDown();

            LedgerTransaction firstResult =
                    first.get(WAIT_SECONDS, TimeUnit.SECONDS);
            LedgerTransaction secondResult =
                    second.get(WAIT_SECONDS, TimeUnit.SECONDS);

            assertEquals(
                    firstResult.id(),
                    secondResult.id(),
                    "Both callers should receive the same transaction"
            );

            assertEquals(
                    new AccountBalance(7_500, BRL),
                    balanceOf(accounts.sourceAccountId())
            );

            assertEquals(
                    new AccountBalance(2_500, BRL),
                    balanceOf(accounts.destinationAId())
            );

            assertEquals(
                    1,
                    countTransferTransactions(accounts.sourceAccountId())
            );

            assertEquals(
                    2,
                    countTransferEntries(accounts.sourceAccountId())
            );

            assertEquals(
                    1,
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM idempotency_requests
                            WHERE holder_id = ?
                              AND operation_type = 'TRANSFER'
                              AND idempotency_key = ?
                            """,
                            Integer.class,
                            accounts.sourceHolderId(),
                            idempotencyKey
                    )
            );
        } finally {
            start.countDown();
            stopExecutor(executor);
        }
    }

    @Test
    void oppositeDirectionTransfersCompleteWithoutDeadlock() throws Exception {
        TestAccounts accounts = createAccounts();

        // Fund both accounts independently so neither opening balance
        // cancels the other.
        postOpeningBalance(
                accounts.sourceAccountId(),
                accounts.fundingAccountId(),
                STARTING_BALANCE
        );

        postOpeningBalance(
                accounts.destinationAId(),
                accounts.fundingAccountId(),
                STARTING_BALANCE
        );

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<TransferOutcome> forward = executor.submit(() -> {
                awaitStart(start);
                return attemptTransfer(
                        accounts.sourceAccountId(),
                        accounts.destinationAId(),
                        1_000,
                        "forward-" + UUID.randomUUID()
                );
            });

            Future<TransferOutcome> reverse = executor.submit(() -> {
                awaitStart(start);
                return attemptTransfer(
                        accounts.destinationAId(),
                        accounts.sourceAccountId(),
                        1_000,
                        "reverse-" + UUID.randomUUID()
                );
            });

            start.countDown();

            TransferOutcome forwardResult =
                    forward.get(WAIT_SECONDS, TimeUnit.SECONDS);
            TransferOutcome reverseResult =
                    reverse.get(WAIT_SECONDS, TimeUnit.SECONDS);

            assertTrue(
                    forwardResult.succeeded(),
                    "Forward transfer should succeed; error: "
                            + forwardResult.errorCode()
            );

            assertTrue(
                    reverseResult.succeeded(),
                    "Reverse transfer should succeed; error: "
                            + reverseResult.errorCode()
            );

            assertEquals(
                    new AccountBalance(10_000, BRL),
                    balanceOf(accounts.sourceAccountId())
            );

            assertEquals(
                    new AccountBalance(10_000, BRL),
                    balanceOf(accounts.destinationAId())
            );

            // Each account is the debit side of exactly one transfer.
            assertEquals(
                    1,
                    countTransferTransactions(accounts.sourceAccountId()),
                    "The forward transfer should debit the source once"
            );

            assertEquals(
                    1,
                    countTransferTransactions(accounts.destinationAId()),
                    "The reverse transfer should debit destination A once"
            );

            // Each transfer has two ledger entries: one debit and one credit.
            assertEquals(
                    2,
                    countTransferEntries(accounts.sourceAccountId()),
                    "The forward transfer should have two ledger entries"
            );

            assertEquals(
                    2,
                    countTransferEntries(accounts.destinationAId()),
                    "The reverse transfer should have two ledger entries"
            );
        } finally {
            start.countDown();
            stopExecutor(executor);
        }
    }

    private TransferOutcome attemptTransfer(
            AccountId source,
            AccountId destination,
            long amountMinor,
            String idempotencyKey) {

        try {
            LedgerTransaction transaction = transferService.execute(
                    command(source, destination, amountMinor, idempotencyKey)
            );

            return new TransferOutcome(transaction, null, idempotencyKey);
        } catch (BusinessException exception) {
            return new TransferOutcome(
                    null,
                    exception.errorCode(),
                    idempotencyKey
            );
        }
    }

    private TransferCommand command(
            AccountId source,
            AccountId destination,
            long amountMinor,
            String idempotencyKey) {

        return new TransferCommand(
                source,
                destination,
                Money.ofMinorUnits(amountMinor, BRL),
                idempotencyKey
        );
    }

    private AccountBalance balanceOf(AccountId accountId) {
        return accountBalanceQuery.findByAccountId(accountId)
                .orElseThrow(() -> new AssertionError(
                        "Missing balance for account " + accountId.value()
                ));
    }

    private TestAccounts createAccounts() {
        UUID sourceHolderId = createHolder();
        UUID destinationAHolderId = createHolder();
        UUID destinationBHolderId = createHolder();
        UUID fundingHolderId = createHolder();

        UUID sourceId = createAccount(sourceHolderId);
        UUID destinationAId = createAccount(destinationAHolderId);
        UUID destinationBId = createAccount(destinationBHolderId);
        UUID fundingId = createAccount(fundingHolderId);

        return new TestAccounts(
                sourceHolderId,
                new AccountId(sourceId),
                new AccountId(destinationAId),
                new AccountId(destinationBId),
                new AccountId(fundingId)
        );
    }

    private UUID createHolder() {
        UUID holderId = UUID.randomUUID();

        String displayName = "T"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 14);

        jdbcTemplate.update(
                """
                INSERT INTO account_holders (
                    id, holder_type, display_name, status
                )
                VALUES (?, 'INDIVIDUAL', ?, 'ACTIVE')
                """,
                holderId,
                displayName
        );

        return holderId;
    }

    private UUID createAccount(UUID holderId) {
        UUID accountId = UUID.randomUUID();

        String accountNumber = "PF"
                + UUID.randomUUID().toString().replace("-", "");

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

    private void postOpeningBalance(
            AccountId creditedAccountId,
            AccountId counterpartyAccountId,
            long amountMinorUnits) {

        UUID transactionId = UUID.randomUUID();

        // The transaction header and both entries must commit together,
        // because the database validates ledger balance at commit.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update(
                    """
                    INSERT INTO ledger_transactions (
                        id, transaction_type, reference
                    )
                    VALUES (?, 'ADJUSTMENT', ?)
                    """,
                    transactionId,
                    "TEST_OPENING_BALANCE"
            );

            insertEntry(
                    transactionId,
                    creditedAccountId,
                    EntryType.CREDIT,
                    amountMinorUnits
            );

            insertEntry(
                    transactionId,
                    counterpartyAccountId,
                    EntryType.DEBIT,
                    amountMinorUnits
            );
        });
    }

    private void insertEntry(
            UUID transactionId,
            AccountId accountId,
            EntryType entryType,
            long amountMinor) {

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
                JOIN ledger_entries e
                  ON e.transaction_id = t.id
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
                JOIN ledger_transactions t
                  ON t.id = e.transaction_id
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

    private int countIdempotencyRecords(
            UUID holderId,
            String firstKey,
            String secondKey) {

        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM idempotency_requests
                WHERE holder_id = ?
                  AND operation_type = 'TRANSFER'
                  AND idempotency_key IN (?, ?)
                """,
                Integer.class,
                holderId,
                firstKey,
                secondKey
        );
    }

    private long countSuccessful(
            TransferOutcome first,
            TransferOutcome second) {

        return (first.succeeded() ? 1 : 0)
                + (second.succeeded() ? 1 : 0);
    }

    private long countInsufficientFunds(
            TransferOutcome first,
            TransferOutcome second) {

        return (first.errorCode() == ErrorCode.INSUFFICIENT_FUNDS ? 1 : 0)
                + (second.errorCode() == ErrorCode.INSUFFICIENT_FUNDS ? 1 : 0);
    }

    private void awaitStart(CountDownLatch start) throws InterruptedException {
        if (!start.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out waiting for concurrent start");
        }
    }

    private void stopExecutor(ExecutorService executor)
            throws InterruptedException {

        executor.shutdownNow();

        assertTrue(
                executor.awaitTermination(5, TimeUnit.SECONDS),
                "Worker threads should terminate"
        );
    }

    private record TestAccounts(
            UUID sourceHolderId,
            AccountId sourceAccountId,
            AccountId destinationAId,
            AccountId destinationBId,
            AccountId fundingAccountId
    ) {}

    private record TransferOutcome(
            LedgerTransaction transaction,
            ErrorCode errorCode,
            String idempotencyKey
    ) {
        boolean succeeded() {
            return transaction != null;
        }
    }
}
