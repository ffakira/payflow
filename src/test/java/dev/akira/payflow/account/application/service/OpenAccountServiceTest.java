
package dev.akira.payflow.account.application.service;

import dev.akira.payflow.account.application.port.out.AccountHolderRepository;
import dev.akira.payflow.account.application.port.out.AccountRepository;
import dev.akira.payflow.account.application.port.out.CurrencyRepository;
import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.account.domain.AccountStatus;
import dev.akira.payflow.account.domain.AccountType;
import dev.akira.payflow.account.domain.holder.AccountHolder;
import dev.akira.payflow.account.domain.holder.AccountHolderStatus;
import dev.akira.payflow.account.domain.holder.AccountHolderType;
import dev.akira.payflow.common.domain.money.CurrencyCode;
import dev.akira.payflow.common.error.BusinessException;
import dev.akira.payflow.common.error.ErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class OpenAccountServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-01-01T00:00:00Z");

    private InMemoryHolderRepository holderRepository;
    private InMemoryAccountRepository accountRepository;
    private InMemoryCurrencyRepository currencyRepository;
    private OpenAccountService service;

    @BeforeEach
    void setUp() {
        holderRepository = new InMemoryHolderRepository();
        accountRepository = new InMemoryAccountRepository();
        currencyRepository = new InMemoryCurrencyRepository();

        RandomAccountNumberGenerator generator = new RandomAccountNumberGenerator();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        service = new OpenAccountService(
                holderRepository,
                accountRepository,
                currencyRepository,
                generator,
                clock
        );
    }

    @Test
    void opensCustomerAccount() {
        OpenAccountCommand command = new OpenAccountCommand(
                AccountHolderType.INDIVIDUAL,
                "Akira",
                CurrencyCode.of("BRL")
        );

        OpenAccountResult result = service.execute(command);

        assertEquals(1, holderRepository.saved.size());
        assertEquals(1, accountRepository.saved.size());

        AccountHolder holder = result.holder();
        Account account = result.account();

        assertEquals(holder.id(), account.holderId());
        assertEquals(AccountHolderType.INDIVIDUAL, holder.type());
        assertEquals("Akira", holder.displayName());
        assertEquals(AccountHolderStatus.ACTIVE, holder.status());
        assertEquals(AccountType.CUSTOMER, account.type());
        assertEquals(AccountStatus.ACTIVE, account.status());
        assertEquals(CurrencyCode.of("BRL"), account.currency());
        assertNotNull(account.accountNumber());
        assertTrue(account.accountNumber().matches("\\d{12}"));
        assertEquals(NOW, holder.createdAt());
        assertEquals(NOW, account.createdAt());
    }

    @Test
    void rejectsNullCommand() {
        assertThrows(
                NullPointerException.class,
                () -> service.execute(null)
        );

        assertTrue(holderRepository.saved.isEmpty());
        assertTrue(accountRepository.saved.isEmpty());
    }

    @Test
    void rejectsBlankDisplayName() {
        OpenAccountCommand command = new OpenAccountCommand(
                AccountHolderType.INDIVIDUAL,
                "   ",
                CurrencyCode.of("BRL")
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.execute(command)
        );

        assertEquals(ErrorCode.DISPLAY_NAME_REQUIRED, exception.errorCode());
        assertTrue(holderRepository.saved.isEmpty());
        assertTrue(accountRepository.saved.isEmpty());
    }

    @Test
    void rejectsDisabledCurrency() {
        currencyRepository.enabled = false;

        OpenAccountCommand command = new OpenAccountCommand(
            AccountHolderType.INDIVIDUAL,
            "Akira",
            CurrencyCode.of("BRL")
        );

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.execute(command)
        );

        assertEquals(ErrorCode.CURRENCY_NOT_ENABLED, exception.errorCode());
        assertTrue(holderRepository.saved.isEmpty());
        assertTrue(accountRepository.saved.isEmpty());
    }

    @Test
    void doesNotSaveAccountIfHolderSaveFails() {
        holderRepository.failOnSave = true;

        OpenAccountCommand command = new OpenAccountCommand(
                AccountHolderType.INDIVIDUAL,
                "Akira",
                CurrencyCode.of("BRL")
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.execute(command)
        );

        assertTrue(accountRepository.saved.isEmpty());
    }

    private static final class InMemoryHolderRepository
            implements AccountHolderRepository {

        private final List<AccountHolder> saved = new ArrayList<>();
        private boolean failOnSave;

        @Override
        public AccountHolder save(AccountHolder holder) {
            if (failOnSave) {
                throw new IllegalStateException("Holder persistence failed");
            }

            saved.add(holder);
            return holder;
        }
    }

    private static final class InMemoryAccountRepository
            implements AccountRepository {

        private final List<Account> saved = new ArrayList<>();

        @Override
        public Account save(Account account) {
            saved.add(account);
            return account;
        }

        @Override
        public Optional<Account> findById(AccountId id) {
            return saved.stream()
                .filter(account -> account.id().equals(id))
                .findFirst();
        }

        @Override
        public Optional<Account> findByIdForUpdate(AccountId id) {
            // in-memory test double: no database row locking is required.
            return findById(id);
        }
    }

    private static final class InMemoryCurrencyRepository
            implements CurrencyRepository {

        private boolean enabled = true;

        @Override
        public boolean isEnabled(CurrencyCode currency) {
            return enabled;
        }
    }
}
