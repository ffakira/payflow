package dev.akira.payflow.account.domain;

import java.time.Instant;
import java.util.Objects;

import dev.akira.payflow.account.domain.holder.AccountHolderId;
import dev.akira.payflow.common.domain.money.CurrencyCode;

public record Account(
        AccountId id,
        AccountHolderId holderId,
        String accountNumber,
        AccountType type,
        CurrencyCode currency,
        AccountStatus status,
        Instant createdAt) {

    public Account {
        Objects.requireNonNull(id, "Account ID must not be null");
        Objects.requireNonNull(holderId, "Holder ID must not be null");
        Objects.requireNonNull(type, "Account type must not be null");
        Objects.requireNonNull(currency, "Currency must not be null");
        Objects.requireNonNull(status, "Status must not be null");
        Objects.requireNonNull(createdAt, "Creation timestamp must not be null");

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
