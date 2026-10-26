package dev.akira.payflow.ledger.domain;

import java.util.Objects;
import java.util.UUID;

public record LedgerTransactionId(UUID value) {
    
    public LedgerTransactionId {
        Objects.requireNonNull(value, "Transaction ID must not be null");
    }

    public static LedgerTransactionId generate() {
        return new LedgerTransactionId(UUID.randomUUID());
    }
}
