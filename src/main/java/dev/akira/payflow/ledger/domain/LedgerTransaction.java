package dev.akira.payflow.ledger.domain;

import java.math.BigInteger;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import dev.akira.payflow.common.domain.money.CurrencyCode;

public record LedgerTransaction(
    LedgerTransactionId id,
    LedgerTransactionType type,
    String reference,
    UUID fxRateSnapshotId,
    LedgerTransactionId reversesTransactionId,
    List<LedgerEntry> entries
) {
    public LedgerTransaction {
        Objects.requireNonNull(id, "Transaction ID must not be null");
        Objects.requireNonNull(type, "Transaction type must not be null");
        Objects.requireNonNull(entries, "Entries must not be null");

        entries = List.copyOf(entries);

        if (reference != null && reference.length() > 100) {
            throw new IllegalArgumentException(
                "Transaction reference must not exceed 100 characters"
            );
        }

        if (type == LedgerTransactionType.FX_CONVERSION && fxRateSnapshotId == null) {
            throw new IllegalArgumentException(
                "FX conversion requires an FX rate snapshot"
            );
        }

        if ((type == LedgerTransactionType.REVERSAL) != (reversesTransactionId != null)) {
            throw new IllegalArgumentException(
                "Only reversal transactions must reference an original transaction"
            );
        }

        if (id.equals(reversesTransactionId)) {
            throw new IllegalArgumentException(
                "A transaction cannot reverse itself"
            );
        }

        if (entries.size() < 2) {
            throw new IllegalArgumentException(
                "A ledger transaction requires at least two entries"
            );
        }

        validateBalance(entries);
    }

    private static void validateBalance(List<LedgerEntry> entries) {
        Map<CurrencyCode, Map<EntryType, BigInteger>> totals = new HashMap<>();

        for (LedgerEntry entry : entries) {
            Map<EntryType, BigInteger> currencyTotals = totals.computeIfAbsent(
                entry.amount().currency(),
                ignored -> new EnumMap<>(EntryType.class)
            );

            currencyTotals.merge(
                entry.type(),
                BigInteger.valueOf(entry.amount().minorUnits()),
                (existing, incoming) -> existing.add(incoming)
            );
        }

        for (Map.Entry<CurrencyCode, Map<EntryType, BigInteger>> currency : totals.entrySet()) {
            Map<EntryType, BigInteger> currencyTotals = currency.getValue();

            BigInteger debits = currencyTotals.getOrDefault(
                EntryType.DEBIT,
                BigInteger.ZERO
            );

            BigInteger credits = currencyTotals.getOrDefault(
                EntryType.CREDIT,
                BigInteger.ZERO
            );

            if (!debits.equals(credits)) {
                throw new IllegalArgumentException(
                    "Transaction is unbalanced for currency "
                        + currency.getKey()
                );
            }
        }
    }

    public static LedgerTransaction create(
        LedgerTransactionType type,
        String reference,
        UUID fxRateSnapshotId,
        LedgerTransactionId reversesTransactionId,
        List<LedgerEntry> entries
    ) {
        return new LedgerTransaction(
            LedgerTransactionId.generate(),
            type,
            reference,
            fxRateSnapshotId,
            reversesTransactionId,
            entries
        );
    }
}
