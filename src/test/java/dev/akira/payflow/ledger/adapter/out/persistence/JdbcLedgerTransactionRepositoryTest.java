package dev.akira.payflow.ledger.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.akira.payflow.account.application.port.out.AccountRepository;
import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.account.domain.AccountType;
import dev.akira.payflow.account.domain.holder.AccountHolderId;
import dev.akira.payflow.common.domain.money.CurrencyCode;
import dev.akira.payflow.common.domain.money.Money;
import dev.akira.payflow.ledger.application.port.out.LedgerTransactionRepository;
import dev.akira.payflow.ledger.application.service.PostLedgerTransactionCommand;
import dev.akira.payflow.ledger.application.service.PostLedgerTransactionService;
import dev.akira.payflow.ledger.domain.EntryType;
import dev.akira.payflow.ledger.domain.LedgerEntry;
import dev.akira.payflow.ledger.domain.LedgerTransaction;
import dev.akira.payflow.ledger.domain.LedgerTransactionId;
import dev.akira.payflow.ledger.domain.LedgerTransactionType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@SpringBootTest
class JdbcLedgerTransactionRepositoryTest {

    @Autowired
    private PostLedgerTransactionService postLedgerTransactionService;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void postsBalancedTransactionAndPersistsHeaderAndEntries() {
        UUID debitHolderUuid = UUID.randomUUID();
        UUID creditHolderUuid = UUID.randomUUID();

        AccountHolderId debitHolderId = new AccountHolderId(debitHolderUuid);
        AccountHolderId creditHolderId = new AccountHolderId(creditHolderUuid);

        insertAccountHolder(debitHolderUuid);
        insertAccountHolder(creditHolderUuid);

        Account debitAccount = createAccount(debitHolderId, "BRL");
        Account creditAccount = createAccount(creditHolderId, "BRL");

        accountRepository.save(debitAccount);
        accountRepository.save(creditAccount);

        Money amount = Money.ofMinorUnits(10_000, CurrencyCode.of("BRL"));

        List<LedgerEntry> entries = List.of(
            LedgerEntry.create(debitAccount.id(), EntryType.DEBIT, amount),
            LedgerEntry.create(creditAccount.id(), EntryType.CREDIT, amount)
        );

        PostLedgerTransactionCommand command = new PostLedgerTransactionCommand(
            LedgerTransactionType.TRANSFER,
            "integration-test-" + UUID.randomUUID(),
            null,
            null,
            entries
        );

        LedgerTransaction transaction = postLedgerTransactionService.execute(command);

        Integer transactionCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM ledger_transactions
            WHERE id = ?
            """,
            Integer.class,
            transaction.id().value()
        );

        Integer entryCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM ledger_entries
            WHERE transaction_id = ?
            """,
            Integer.class,
            transaction.id().value()
        );

        assertNotNull(transaction.id());
        assertEquals(1, transactionCount);
        assertEquals(2, entryCount);

        List<String> persistedTypes = jdbcTemplate.query(
            """
            SELECT entry_type
            FROM ledger_entries
            WHERE transaction_id = ?
            ORDER BY entry_type
            """,
            (rs, rowNum) -> rs.getString("entry_type"),
            transaction.id().value()
        );

        assertEquals(List.of("CREDIT", "DEBIT"), persistedTypes);

        List<Long> persistedAmounts = jdbcTemplate.query(
            """
            SELECT amount_minor
            FROM ledger_entries
            WHERE transaction_id = ?
            ORDER BY entry_type
            """,
            (rs, rowNum) -> rs.getLong("amount_minor"),
            transaction.id().value()
        );

        assertEquals(List.of(10_000L, 10_000L), persistedAmounts);
    }

    @Test
    void rejectsUnbalancedTransactionWithoutPersistingRecords() {
        UUID holderUuid = UUID.randomUUID();
        AccountHolderId holderId = new AccountHolderId(holderUuid);

        insertAccountHolder(holderUuid);

        Account debitAccount = createAccount(holderId, "BRL");
        accountRepository.save(debitAccount);

        Money debitAmount = Money.ofMinorUnits(10_000, CurrencyCode.of("BRL"));
        Money creditAmount = Money.ofMinorUnits(9_000, CurrencyCode.of("BRL"));

        List<LedgerEntry> entries = List.of(
            LedgerEntry.create(debitAccount.id(), EntryType.DEBIT, debitAmount),
            LedgerEntry.create(debitAccount.id(), EntryType.CREDIT, creditAmount)
        );

        PostLedgerTransactionCommand command = new PostLedgerTransactionCommand(
            LedgerTransactionType.TRANSFER,
            "rollback-test-" + UUID.randomUUID(),
            null,
            null,
            entries
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> postLedgerTransactionService.execute(command)
        );
    }

    @Test
    void rollsBackTransactionHeaderWhenEntryPersistenceFails() {
        LedgerTransaction transaction = LedgerTransaction.create(
            LedgerTransactionType.TRANSFER,
            "rollback-test-" + UUID.randomUUID(),
            null,
            null,
            List.of(
                LedgerEntry.create(
                    AccountId.generate(),
                    EntryType.DEBIT,
                    Money.ofMinorUnits(
                        10_000, 
                        CurrencyCode.of("BRL")
                    )
                ),
                LedgerEntry.create(
                    AccountId.generate(),
                    EntryType.CREDIT,
                    Money.ofMinorUnits(
                        10_000,
                        CurrencyCode.of("BRL")
                    )
                )
            )
        );

        assertThrows(
            DataAccessException.class,
            () -> ledgerTransactionRepository.save(transaction)
        );

        Integer transactionCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM ledger_transactions
            WHERE id = ?
            """,
            Integer.class,
            transaction.id().value()
        );

        Integer entryCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM ledger_entries
            WHERE transaction_id = ?
            """,
            Integer.class,
            transaction.id().value()
        );

        assertEquals(0, transactionCount);
        assertEquals(0, entryCount);
    }

    @Test
    void retrievesPersistedTransactionWithEntries() {
        UUID debitHolderUuid = UUID.randomUUID();
        UUID creditHolderUuid = UUID.randomUUID();

        AccountHolderId debitHolderId = new AccountHolderId(debitHolderUuid);
        AccountHolderId creditHolderId = new AccountHolderId(creditHolderUuid);

        insertAccountHolder(debitHolderUuid);
        insertAccountHolder(creditHolderUuid);

        Account debitAccount = createAccount(debitHolderId, "BRL");
        Account creditAccount = createAccount(creditHolderId, "BRL");

        accountRepository.save(debitAccount);
        accountRepository.save(creditAccount);

        PostLedgerTransactionCommand command = new PostLedgerTransactionCommand(
            LedgerTransactionType.TRANSFER,
            "TEST-" + UUID.randomUUID(),
            null,
            null,
            List.of(
                LedgerEntry.create(
                    debitAccount.id(),
                    EntryType.DEBIT,
                    Money.ofMinorUnits(10_000, CurrencyCode.of("BRL"))
                ),
                LedgerEntry.create(
                    creditAccount.id(),
                    EntryType.CREDIT,
                    Money.ofMinorUnits(10_000, CurrencyCode.of("BRL"))
                )
            )
        );

        LedgerTransaction saved = postLedgerTransactionService.execute(command);

        LedgerTransaction retrieved = ledgerTransactionRepository
            .findById(saved.id())
            .orElseThrow();

        assertEquals(saved.id(), retrieved.id());
        assertEquals(saved.type(), retrieved.type());
        assertEquals(saved.reference(), retrieved.reference());
        assertEquals(saved.fxRateSnapshotId(), retrieved.fxRateSnapshotId());
        assertEquals(saved.reversesTransactionId(), retrieved.reversesTransactionId());
        assertEquals(saved.entries().size(), retrieved.entries().size());
        assertEquals(Set.copyOf(saved.entries()), Set.copyOf(retrieved.entries()));
    }

    @Test
    void returnsEmptyWhenTransactionDoesNotExist() {
        LedgerTransactionId id = new LedgerTransactionId(UUID.randomUUID());

        assertEquals(
            Optional.empty(),
            ledgerTransactionRepository.findById(id)
        );
    }

    @Test
    void rejectsNullTransactionId() {
        assertThrows(
            NullPointerException.class,
            () -> ledgerTransactionRepository.findById(null)
        );
    }

    @Test
    void rejectsNullTransaction() {
        assertThrows(
            NullPointerException.class, 
            () -> ledgerTransactionRepository.save(null)
        );
    }

    private Account createAccount(AccountHolderId holderId, String currencyCode) {
        return Account.open(
            AccountId.generate(),
            holderId,
            "PF" + UUID.randomUUID().toString().replace("-", ""),
            AccountType.CUSTOMER,
            CurrencyCode.of(currencyCode),
            Instant.now()
        );
    }

    private void insertAccountHolder(UUID holderId) {
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
            "T" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 14)
        );
    }
}
