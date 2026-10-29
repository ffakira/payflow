package dev.akira.payflow.transfer.application.service;

import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.common.domain.money.Money;

import java.util.Objects;

public record InitiateTransferCommand(
        String username,
        String rawPin,
        AccountId sourceAccountId,
        AccountId destinationAccountId,
        Money amount,
        String idempotencyKey
) {
    public InitiateTransferCommand {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or blank");
        }

        if (rawPin == null || rawPin.isBlank()) {
            throw new IllegalArgumentException("Raw pin cannot be null or blank");
        }

        username = username.strip();
        Objects.requireNonNull(sourceAccountId, "Source account ID must not be null");
        Objects.requireNonNull(destinationAccountId, "Destination account ID must not be null");
        Objects.requireNonNull(sourceAccountId, "Source account ID must not be null");

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key must not be null or blank");
        }

        idempotencyKey = idempotencyKey.strip();
    }
}
