package dev.akira.payflow.account.domain;

import dev.akira.payflow.account.domain.holder.AccountHolderId;
import dev.akira.payflow.common.domain.money.CurrencyCode;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountTest {

    private static final AccountId ACCOUNT_ID =
        new AccountId(UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
        ));

    private static final AccountHolderId HOLDER_ID =
        new AccountHolderId(UUID.fromString(
            "00000000-0000-0000-0000-000000000002"
        ));

    private static final Instant CREATED_AT =
        Instant.parse("2026-01-01T00:00:00Z");

    private Account openAccount() {
        return Account.open(
            ACCOUNT_ID,
            HOLDER_ID,
            "PF000000000001",
            AccountType.CUSTOMER,
            CurrencyCode.of("BRL"),
            CREATED_AT
        );
    }

    @Test
    void opensAccountAsActive() {
        Account account = openAccount();

        assertEquals(ACCOUNT_ID, account.id());
        assertEquals(HOLDER_ID, account.holderId());
        assertEquals("PF000000000001", account.accountNumber());
        assertEquals(AccountType.CUSTOMER, account.type());
        assertEquals(CurrencyCode.of("BRL"), account.currency());
        assertEquals(AccountStatus.ACTIVE, account.status());
        assertEquals(CREATED_AT, account.createdAt());
        assertTrue(account.isActive());
        assertFalse(account.isSuspended());
        assertFalse(account.isClosed());
    }

    @Test
    void suspendsActiveAccount() {
        Account account = openAccount();

        Account suspended = account.suspend();

        assertEquals(AccountStatus.SUSPENDED, suspended.status());
        assertTrue(suspended.isSuspended());
        assertFalse(suspended.isActive());

        // The original immutable instance remains unchanged.
        assertEquals(AccountStatus.ACTIVE, account.status());
    }

    @Test
    void cannotSuspendAnAlreadySuspendedAccount() {
        Account suspended = openAccount().suspend();

        assertThrows(
            IllegalStateException.class,
            suspended::suspend
        );
    }

    @Test
    void reactivatesSuspendedAccount() {
        Account suspended = openAccount().suspend();

        Account reactivated = suspended.reactivate();

        assertEquals(AccountStatus.ACTIVE, reactivated.status());
        assertTrue(reactivated.isActive());

        // The original instance remains suspended.
        assertEquals(AccountStatus.SUSPENDED, suspended.status());
    }

    @Test
    void cannotReactivateAnActiveAccount() {
        Account active = openAccount();

        assertThrows(
            IllegalStateException.class,
            active::reactivate
        );
    }

    @Test
    void closesActiveAccount() {
        Account closed = openAccount().close();

        assertEquals(AccountStatus.CLOSED, closed.status());
        assertTrue(closed.isClosed());
        assertFalse(closed.isActive());
    }

    @Test
    void closesSuspendedAccount() {
        Account closed = openAccount()
            .suspend()
            .close();

        assertEquals(AccountStatus.CLOSED, closed.status());
    }

    @Test
    void cannotCloseAnAlreadyClosedAccount() {
        Account closed = openAccount().close();

        assertThrows(
            IllegalStateException.class,
            closed::close
        );
    }

    @Test
    void cannotSuspendAClosedAccount() {
        Account closed = openAccount().close();

        assertThrows(
            IllegalStateException.class,
            closed::suspend
        );
    }

    @Test
    void cannotReactivateAClosedAccount() {
        Account closed = openAccount().close();

        assertThrows(
            IllegalStateException.class,
            closed::reactivate
        );
    }

    @Test
    void rejectsBlankAccountNumber() {
        assertThrows(
            IllegalArgumentException.class,
            () -> Account.open(
                ACCOUNT_ID,
                HOLDER_ID,
                " ",
                AccountType.CUSTOMER,
                CurrencyCode.of("BRL"),
                CREATED_AT
            )
        );
    }

    @Test
    void rejectsAccountNumberLongerThan34Characters() {
        String accountNumber = "A".repeat(35);

        assertThrows(
            IllegalArgumentException.class,
            () -> Account.open(
                ACCOUNT_ID,
                HOLDER_ID,
                accountNumber,
                AccountType.CUSTOMER,
                CurrencyCode.of("BRL"),
                CREATED_AT
            )
        );
    }

    @Test
    void rejectsNullCurrency() {
        assertThrows(
            NullPointerException.class,
            () -> Account.open(
                ACCOUNT_ID,
                HOLDER_ID,
                "PF000000000001",
                AccountType.CUSTOMER,
                null,
                CREATED_AT
            )
        );
    }
}
