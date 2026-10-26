package dev.akira.payflow.account.domain;

import java.util.Objects;
import java.util.UUID;

public record AccountId(UUID value) {

    public AccountId {
        Objects.requireNonNull(value, "Account ID must not be null");
    }

    public static AccountId generate() {
        return new AccountId(UUID.randomUUID());
    }

    public static AccountId of(String value) {
        Objects.requireNonNull(value, "Account ID must not be null");
        return new AccountId(UUID.fromString(value));
    }
}
