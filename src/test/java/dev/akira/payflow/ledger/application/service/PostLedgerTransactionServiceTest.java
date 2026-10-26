package dev.akira.payflow.ledger.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.akira.payflow.account.application.port.out.AccountRepository;
import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.account.domain.AccountType;
import dev.akira.payflow.account.domain.holder.AccountHolderId;
import dev.akira.payflow.common.domain.money.CurrencyCode;
import dev.akira.payflow.common.domain.money.Money;
import dev.akira.payflow.ledger.application.port.out.LedgerTransactionRepository;
import dev.akira.payflow.ledger.domain.EntryType;
import dev.akira.payflow.ledger.domain.LedgerEntry;
import dev.akira.payflow.ledger.domain.LedgerTransaction;
import dev.akira.payflow.ledger.domain.LedgerTransactionId;
import dev.akira.payflow.ledger.domain.LedgerTransactionType;

public class PostLedgerTransactionServiceTest {
    
    private static final CurrencyCode BRL = CurrencyCode.of("BRL");

    private InMemoryAccountRepository accountRepository;
    private InMemoryLedgerTransactionRepository ledgerRepository;
    private PostLedgerTransactionService service;

    private Account debitAccount;
    private Account creditAccount;

    @BeforeEach
    void setUp() {
        accountRepository = new InMemoryAccountRepository();
        ledgerRepository = new InMemoryLedgerTransactionRepository();

        service = new PostLedgerTransactionService(
            accountRepository,
            ledgerRepository
        );

        AccountHolderId holderId = new AccountHolderId(UUID.randomUUID());

        debitAccount = Account.open(
            new AccountId(UUID.randomUUID()),
            holderId,
            "100000000001",
            AccountType.CUSTOMER,
            BRL,
            Instant.now()
        );

        creditAccount = Account.open(
            new AccountId(UUID.randomUUID()),
            holderId,
            "100000000002",
            AccountType.CUSTOMER,
            BRL,
            Instant.now()
        );

        accountRepository.save(debitAccount);
        accountRepository.save(creditAccount);
    }

    @Test
    void postsBalancedTransaction() {
        PostLedgerTransactionCommand command = command(
            EntryType.DEBIT,
            debitAccount,
            EntryType.CREDIT,
            creditAccount
        );

        LedgerTransaction result = service.execute(command);

        assertNotNull(result);
        assertEquals(LedgerTransactionType.TRANSFER, result.type());
        assertEquals(2, result.entries().size());
        assertEquals(1, ledgerRepository.saved.size());
        assertEquals(result, ledgerRepository.saved.getFirst());
    }

    @Test
    void rejectsMissingAccount() {
        LedgerEntry debit = entry(
            EntryType.DEBIT,
            debitAccount,
            1_000
        );

        LedgerEntry credit = LedgerEntry.create(
            new AccountId(UUID.randomUUID()),
            EntryType.CREDIT,
            Money.ofMinorUnits(1_000, BRL)
        );

        PostLedgerTransactionCommand command = new PostLedgerTransactionCommand(
            LedgerTransactionType.TRANSFER,
            null,
            null,
            null,
            List.of(debit, credit)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> service.execute(command)
        );

        assertTrue(ledgerRepository.saved.isEmpty());
    }

    @Test
    void rejectsSuspendedAccount() {
        Account suspendedAccount = debitAccount.suspend();

        accountRepository.save(suspendedAccount);

        PostLedgerTransactionCommand command = command(
            EntryType.DEBIT,
            suspendedAccount,
            EntryType.CREDIT,
            creditAccount
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> service.execute(command)
        );

        assertTrue(ledgerRepository.saved.isEmpty());
    }

    @Test
    void rejectsEntryCurrencyDifferentFromAccountCurrency() {
        LedgerEntry debit = entry(
            EntryType.DEBIT,
            debitAccount,
            1_000
        );

        LedgerEntry credit = LedgerEntry.create(
            creditAccount.id(),
            EntryType.CREDIT,
            Money.ofMinorUnits(1_000, CurrencyCode.of("USD"))
        );

        PostLedgerTransactionCommand command = new PostLedgerTransactionCommand(
            LedgerTransactionType.TRANSFER,
            null,
            null,
            null,
            List.of(debit, credit)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> service.execute(command)
        );

        assertTrue(ledgerRepository.saved.isEmpty());
    }

    @Test
    void rejectsUnbalancedTransactionBeforePersistence() {
        PostLedgerTransactionCommand command = new PostLedgerTransactionCommand(
            LedgerTransactionType.TRANSFER,
            null,
            null,
            null,
            List.of(
                entry(EntryType.DEBIT, debitAccount, 1_000),
                entry(EntryType.CREDIT, creditAccount, 900)
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> service.execute(command)
        );

        assertTrue(ledgerRepository.saved.isEmpty());
    }

    @Test
    void inMemoryRepositoryFindsSavedTransactionById() {
        PostLedgerTransactionCommand command = command(
            EntryType.DEBIT,
            debitAccount,
            EntryType.CREDIT,
            creditAccount
        );

        LedgerTransaction saved = service.execute(command);

        Optional<LedgerTransaction> result =
            ledgerRepository.findById(saved.id());

        assertTrue(result.isPresent());
        assertEquals(saved, result.orElseThrow());
    }

    @Test
    void inMemoryRepositoryReturnsEmptyForUnknownId() {
        Optional<LedgerTransaction> result =
            ledgerRepository.findById(LedgerTransactionId.generate());

        assertTrue(result.isEmpty());
    }

    private PostLedgerTransactionCommand command(
        EntryType debitType,
        Account debit,
        EntryType creditType,
        Account credit
    ) {
        return new PostLedgerTransactionCommand(
            LedgerTransactionType.TRANSFER,
            null,
            null,
            null,
            List.of(
                entry(debitType, debit, 1_000),
                entry(creditType, credit, 1_000)
            )
        );
    }

    private LedgerEntry entry(
        EntryType type,
        Account account,
        long amountMinor
    ) {
        return LedgerEntry.create(
            account.id(),
            type,
            Money.ofMinorUnits(amountMinor, account.currency())
        );
    }

    private static final class InMemoryAccountRepository implements AccountRepository {
        
        private final List<Account> saved = new ArrayList<>();

        @Override
        public Account save(Account account) {
            saved.removeIf(existing ->
                existing.id().equals(account.id())
            );

            saved.add(account);
            return account;
        }

        @Override
        public Optional<Account> findById(AccountId id) {
            return saved.stream()
                .filter(account -> account.id().equals(id))
                .findFirst();
        }
    }

    private static final class InMemoryLedgerTransactionRepository implements LedgerTransactionRepository {

        private final List<LedgerTransaction> saved = new ArrayList<>();

        @Override
        public LedgerTransaction save(LedgerTransaction transaction) {
            saved.add(transaction);
            return transaction;
        }

        @Override
        public Optional<LedgerTransaction> findById(LedgerTransactionId id) {
            return saved.stream()
                .filter(transaction -> transaction.id().equals(id))
                .findFirst();
        }
    }
}
