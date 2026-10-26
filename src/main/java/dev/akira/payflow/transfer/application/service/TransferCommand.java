package dev.akira.payflow.transfer.application.service;

import java.util.Objects;

import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.common.domain.money.Money;

public record TransferCommand(
    AccountId sourceAccountId,
    AccountId destinationAccountId,
    Money amount,
    String idempotencyKey
) {
    public TransferCommand {
        Objects.requireNonNull(sourceAccountId, "Souce account id must not be null");
        Objects.requireNonNull(destinationAccountId, "Destination account id must not be null");
        Objects.requireNonNull(amount, "Transfer amount must not be null");

        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }

        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key must not be blank");
        }

        idempotencyKey = idempotencyKey.strip();

        if (idempotencyKey.isEmpty() || idempotencyKey.length() > 255) {
            throw new IllegalArgumentException("Idempotency key must contain 1 to 255 characters");
        }
    }    
}
