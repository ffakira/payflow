package dev.akira.payflow.transfer.application.port.out;

import dev.akira.payflow.ledger.domain.LedgerTransactionId;

import java.util.Objects;

public record TransferIdempotencyRecord(
    String requestHash,
    LedgerTransactionId transactionId
) {
    public TransferIdempotencyRecord {
        Objects.requireNonNull(requestHash, "Request hash must not be null");
        Objects.requireNonNull(transactionId, "Transaction id must not be null");

        if (!requestHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Request hash must be a lowercase SHA-256 hex string");
        }
    }
}
