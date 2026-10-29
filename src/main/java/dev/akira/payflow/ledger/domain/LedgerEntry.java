package dev.akira.payflow.ledger.domain;

import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.common.domain.money.Money;

import java.util.Objects;
import java.util.UUID;

public record LedgerEntry(
    UUID id,
    AccountId accountId,
    EntryType type,
    Money amount
) {
    public LedgerEntry {
        Objects.requireNonNull(id, "Entry ID must not be null");
        Objects.requireNonNull(accountId, "Account ID must not be null");
        Objects.requireNonNull(type, "Entry type must not be null");
        Objects.requireNonNull(amount, "Entry amount must not be null");

        if (!amount.isPositive()) {
            throw new IllegalArgumentException(
                "Ledger entry amount must be positive"
            );
        }
    }

    public static LedgerEntry create(
        AccountId accountId,
        EntryType type,
        Money amount
    ) {
        return new LedgerEntry(
            UUID.randomUUID(),
            accountId,
            type,
            amount
        );
    }
}
