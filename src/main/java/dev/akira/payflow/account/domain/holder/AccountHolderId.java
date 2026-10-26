package dev.akira.payflow.account.domain.holder;

import java.util.Objects;
import java.util.UUID;

public record AccountHolderId(UUID value) {

    public AccountHolderId {
        Objects.requireNonNull(value, "value cannot be null");
    }

    public static AccountHolderId generate() {
        return new AccountHolderId(UUID.randomUUID());
    }
}
