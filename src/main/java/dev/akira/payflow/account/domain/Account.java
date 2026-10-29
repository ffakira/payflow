package dev.akira.payflow.account.domain;

import dev.akira.payflow.account.domain.holder.AccountHolderId;
import dev.akira.payflow.common.domain.money.CurrencyCode;
import dev.akira.payflow.common.domain.validation.NotNull;

import java.time.Instant;

public record Account(
        @NotNull(message = "Account ID must not be null")
        AccountId id,

        @NotNull(message = "Holder ID must not be null")
        AccountHolderId holderId,

        @NotNull(message = "Account number must not be null")
        String accountNumber,

        @NotNull(message = "Account type must not be null")
        AccountType type,

        @NotNull(message = "Currency must not be null")
        CurrencyCode currency,

        @NotNull(message = "Status must not be null")
        AccountStatus status,

        @NotNull(message = "Creation timestamp must not be null")
        Instant createdAt) {

    public Account {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "Account number must not be blank");
        }

        if (accountNumber.length() > 34) {
            throw new IllegalArgumentException(
                    "Account number must not exceed 34 characters");
        }
    }

    public static Account open(
            AccountId id,
            AccountHolderId holderId,
            String accountNumber,
            AccountType type,
            CurrencyCode currency,
            Instant createdAt) {
        return new Account(
                id,
                holderId,
                accountNumber,
                type,
                currency,
                AccountStatus.ACTIVE,
                createdAt);
    }

    public Account suspend() {
        requireStatus(
                AccountStatus.ACTIVE,
                "Only active accounts can be suspended");

        return withStatus(AccountStatus.SUSPENDED);
    }

    public Account reactivate() {
        requireStatus(
                AccountStatus.SUSPENDED,
                "Only suspended accounts can be reactivated");

        return withStatus(AccountStatus.ACTIVE);
    }

    public Account close() {
        if (status == AccountStatus.CLOSED) {
            throw new IllegalStateException(
                    "Account is already closed");
        }

        return withStatus(AccountStatus.CLOSED);
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean isSuspended() {
        return status == AccountStatus.SUSPENDED;
    }

    public boolean isClosed() {
        return status == AccountStatus.CLOSED;
    }

    private void requireStatus(
            AccountStatus expectedStatus,
            String message) {
        if (status != expectedStatus) {
            throw new IllegalStateException(message);
        }
    }

    private Account withStatus(AccountStatus newStatus) {
        return new Account(
                id,
                holderId,
                accountNumber,
                type,
                currency,
                newStatus,
                createdAt);
    }
}
