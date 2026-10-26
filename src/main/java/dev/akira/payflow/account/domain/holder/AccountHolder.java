package dev.akira.payflow.account.domain.holder;

import java.time.Instant;
import java.util.Objects;

public record AccountHolder(
    AccountHolderId id,
    AccountHolderType type,
    String displayName,
    AccountHolderStatus status,
    Instant createdAt
) {
    public AccountHolder {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");

        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException(
                "displayName cannot be blank"
            );
        }

        displayName = displayName.strip();
    }

    public boolean isActive() {
        return status == AccountHolderStatus.ACTIVE;
    }
}
