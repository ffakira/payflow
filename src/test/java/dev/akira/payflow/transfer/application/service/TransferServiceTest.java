package dev.akira.payflow.transfer.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import dev.akira.payflow.account.application.port.out.AccountBalanceQuery;
import dev.akira.payflow.account.application.port.out.AccountRepository;
import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.account.domain.AccountBalance;
import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.account.domain.AccountType;
import dev.akira.payflow.account.domain.holder.AccountHolderId;
import dev.akira.payflow.common.domain.money.CurrencyCode;
import dev.akira.payflow.common.domain.money.Money;
import dev.akira.payflow.common.error.BusinessException;
import dev.akira.payflow.common.error.ErrorCode;
import dev.akira.payflow.ledger.application.port.out.LedgerTransactionRepository;
import dev.akira.payflow.ledger.application.service.PostLedgerTransactionService;
import dev.akira.payflow.ledger.domain.EntryType;
import dev.akira.payflow.ledger.domain.LedgerTransaction;
import dev.akira.payflow.ledger.domain.LedgerTransactionId;
import dev.akira.payflow.ledger.domain.LedgerTransactionType;
import dev.akira.payflow.transfer.application.port.out.TransferIdempotencyRecord;
import dev.akira.payflow.transfer.application.port.out.TransferIdempotencyRepository;

class TransferServiceTest {

    private static final CurrencyCode BRL = CurrencyCode.of("BRL");
    private static final CurrencyCode USD = CurrencyCode.of("USD");

    private InMemoryAccountRepository accountRepository;
    private InMemoryBalanceQuery balanceQuery;
    private InMemoryLedgerRepository ledgerRepository;
    private InMemoryIdempotencyRepository idempotencyRepository;

    private TransferService service;

    private Account source;
    private Account destination;

    @BeforeEach
    void setUp() {
        accountRepository = new InMemoryAccountRepository();
        balanceQuery = new InMemoryBalanceQuery();
        ledgerRepository = new InMemoryLedgerRepository();
        idempotencyRepository = new InMemoryIdempotencyRepository();

        AccountHolderId holderId =
            new AccountHolderId(UUID.randomUUID());

        source = Account.open(
            AccountId.generate(),
            holderId,
            "100000000001",
            AccountType.CUSTOMER,
            BRL,
            Instant.now()
        );

        destination = Account.open(
            AccountId.generate(),
            holderId,
            "100000000002",
            AccountType.CUSTOMER,
            BRL,
            Instant.now()
        );

        accountRepository.save(source);
        accountRepository.save(destination);

        balanceQuery.setBalance(source.id(), 10_000, BRL);
        balanceQuery.setBalance(destination.id(), 0, BRL);

        PostLedgerTransactionService ledgerService =
            new PostLedgerTransactionService(
                accountRepository,
                ledgerRepository
            );

        PlatformTransactionManager transactionManager =
            new TestTransactionManager();

        service = new TransferService(
            accountRepository,
            balanceQuery,
            ledgerService,
            ledgerRepository,
            idempotencyRepository,
            transactionManager
        );
    }

    @Test
    void executesTransferBetweenCustomerAccounts() {
        TransferCommand command = command(
            source.id(),
            destination.id(),
            2_500,
            "transfer-001"
        );

        LedgerTransaction result = service.execute(command);

        assertEquals(LedgerTransactionType.TRANSFER, result.type());
        assertEquals(2, result.entries().size());

        assertEquals(
            source.id(),
            result.entries().get(0).accountId()
        );
        assertEquals(
            EntryType.DEBIT,
            result.entries().get(0).type()
        );
        assertEquals(
            Money.ofMinorUnits(2_500, BRL),
            result.entries().get(0).amount()
        );

        assertEquals(
            destination.id(),
            result.entries().get(1).accountId()
        );
        assertEquals(
            EntryType.CREDIT,
            result.entries().get(1).type()
        );
        assertEquals(
            Money.ofMinorUnits(2_500, BRL),
            result.entries().get(1).amount()
        );

        assertEquals(1, ledgerRepository.saved.size());
        assertEquals(1, idempotencyRepository.records.size());
    }

    @Test
    void rejectsTransferWhenFundsAreInsufficient() {
        TransferCommand command = command(
            source.id(),
            destination.id(),
            20_000,
            "transfer-insufficient"
        );

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.execute(command)
        );

        assertEquals(
            ErrorCode.INSUFFICIENT_FUNDS,
            exception.errorCode()
        );

        assertTrue(ledgerRepository.saved.isEmpty());
        assertTrue(idempotencyRepository.records.isEmpty());
    }

    @Test
    void rejectsTransferWhenSourceAccountIsInactive() {
        Account suspendedSource = source.suspend();
        accountRepository.save(suspendedSource);

        TransferCommand command = command(
            source.id(),
            destination.id(),
            1_000,
            "transfer-inactive"
        );

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.execute(command)
        );

        assertEquals(
            ErrorCode.ACCOUNT_NOT_ACTIVE,
            exception.errorCode()
        );

        assertTrue(ledgerRepository.saved.isEmpty());
        assertTrue(idempotencyRepository.records.isEmpty());
    }

    @Test
    void rejectsTransferWhenCurrenciesDoNotMatch() {
        Account usdDestination = Account.open(
            AccountId.generate(),
            source.holderId(),
            "100000000003",
            AccountType.CUSTOMER,
            USD,
            Instant.now()
        );

        accountRepository.save(usdDestination);

        balanceQuery.setBalance(usdDestination.id(), 0, USD);

        TransferCommand command = command(
            source.id(),
            usdDestination.id(),
            1_000,
            "transfer-currency"
        );

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.execute(command)
        );

        assertEquals(
            ErrorCode.CURRENCY_MISMATCH,
            exception.errorCode()
        );

        assertTrue(ledgerRepository.saved.isEmpty());
        assertTrue(idempotencyRepository.records.isEmpty());
    }

    @Test
    void returnsOriginalTransactionForRepeatedIdenticalRequest() {
        TransferCommand command = command(
            source.id(),
            destination.id(),
            2_500,
            "transfer-idempotent"
        );

        LedgerTransaction first = service.execute(command);
        LedgerTransaction second = service.execute(command);

        assertEquals(first, second);
        assertEquals(1, ledgerRepository.saved.size());
        assertEquals(1, idempotencyRepository.records.size());
    }

    @Test
    void rejectsReusingIdempotencyKeyForDifferentRequest() {
        TransferCommand firstCommand = command(
            source.id(),
            destination.id(),
            2_500,
            "transfer-reused-key"
        );

        service.execute(firstCommand);

        TransferCommand differentCommand = command(
            source.id(),
            destination.id(),
            3_000,
            "transfer-reused-key"
        );

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.execute(differentCommand)
        );

        assertEquals(
            ErrorCode.IDEMPOTENCY_KEY_REUSED,
            exception.errorCode()
        );

        assertEquals(1, ledgerRepository.saved.size());
        assertEquals(1, idempotencyRepository.records.size());
    }

    private TransferCommand command(
        AccountId sourceId,
        AccountId destinationId,
        long amountMinor,
        String idempotencyKey
    ) {
        return new TransferCommand(
            sourceId,
            destinationId,
            Money.ofMinorUnits(amountMinor, BRL),
            idempotencyKey
        );
    }

    private static final class TestTransactionManager
            implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(
            TransactionDefinition definition
        ) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            // Unit-test transaction boundary only.
        }

        @Override
        public void rollback(TransactionStatus status) {
            // Unit-test transaction boundary only.
        }
    }

    private static final class InMemoryAccountRepository
            implements AccountRepository {

        private final Map<AccountId, Account> accounts = new HashMap<>();

        @Override
        public Account save(Account account) {
            accounts.put(account.id(), account);
            return account;
        }

        @Override
        public Optional<Account> findById(AccountId id) {
            return Optional.ofNullable(accounts.get(id));
        }

        @Override
        public Optional<Account> findByIdForUpdate(AccountId id) {
            // In-memory fake: it does not provide real row locking.
            return findById(id);
        }
    }

    private static final class InMemoryBalanceQuery
            implements AccountBalanceQuery {

        private final Map<AccountId, AccountBalance> balances =
            new HashMap<>();

        void setBalance(
            AccountId accountId,
            long minorUnits,
            CurrencyCode currency
        ) {
            balances.put(
                accountId,
                new AccountBalance(minorUnits, currency)
            );
        }

        @Override
        public Optional<AccountBalance> findByAccountId(
            AccountId accountId
        ) {
            return Optional.ofNullable(balances.get(accountId));
        }
    }

    private static final class InMemoryLedgerRepository
            implements LedgerTransactionRepository {

        private final List<LedgerTransaction> saved = new ArrayList<>();

        @Override
        public LedgerTransaction save(LedgerTransaction transaction) {
            saved.add(transaction);
            return transaction;
        }

        @Override
        public Optional<LedgerTransaction> findById(
            LedgerTransactionId id
        ) {
            return saved.stream()
                .filter(transaction -> transaction.id().equals(id))
                .findFirst();
        }
    }

    private static final class InMemoryIdempotencyRepository
            implements TransferIdempotencyRepository {

        private final Map<String, TransferIdempotencyRecord> records =
            new HashMap<>();

        private String key(
            AccountHolderId holderId,
            String operationType,
            String idempotencyKey
        ) {
            return holderId.value()
                + "|" + operationType
                + "|" + idempotencyKey;
        }

        @Override
        public Optional<TransferIdempotencyRecord> find(
            AccountHolderId holderId,
            String operationType,
            String idempotencyKey
        ) {
            return Optional.ofNullable(
                records.get(key(holderId, operationType, idempotencyKey))
            );
        }

        @Override
        public void save(
            AccountHolderId holderId,
            String operationType,
            String idempotencyKey,
            String requestHash,
            LedgerTransactionId transactionId
        ) {
            records.put(
                key(holderId, operationType, idempotencyKey),
                new TransferIdempotencyRecord(requestHash, transactionId)
            );
        }
    }
}
