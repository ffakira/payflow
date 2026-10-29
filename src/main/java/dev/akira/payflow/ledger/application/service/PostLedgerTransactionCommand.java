package dev.akira.payflow.ledger.application.service;

import dev.akira.payflow.ledger.domain.LedgerEntry;
import dev.akira.payflow.ledger.domain.LedgerTransactionId;
import dev.akira.payflow.ledger.domain.LedgerTransactionType;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PostLedgerTransactionCommand(
    LedgerTransactionType type,
    String reference,
    UUID fxRateSnapshotId,
    LedgerTransactionId reversesTransactionId,
    List<LedgerEntry> entries
) {
    public PostLedgerTransactionCommand {
        Objects.requireNonNull(type, "Transaction type must not be null");
        Objects.requireNonNull(entries, "Entries must not be null");

        entries = List.copyOf(entries);
    }
}
