package dev.akira.payflow.ledger.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.common.domain.money.CurrencyCode;
import dev.akira.payflow.common.domain.money.Money;

public class LedgerTransactionTest {
    
    private static final CurrencyCode BRL = CurrencyCode.of("BRL");
    private static final CurrencyCode USD = CurrencyCode.of("USD");

    private LedgerEntry entry(
        EntryType type,
        long minorUnits,
        CurrencyCode currency
    ) {
        return LedgerEntry.create(
            new AccountId(UUID.randomUUID()),
            type,
            Money.ofMinorUnits(minorUnits, currency)
        );
    }

    private LedgerTransaction transaction(List<LedgerEntry> entries) {
        return LedgerTransaction.create(
            LedgerTransactionType.TRANSFER,
            null,
            null,
            null,
            entries
        );
    }

    @Test
    void acceptsBalancedEntriesInSameCurrency() {
        LedgerTransaction transaction = transaction(List.of(
            entry(EntryType.DEBIT, 1_000, BRL),
            entry(EntryType.CREDIT, 1_000, BRL)
        ));

        assertEquals(2, transaction.entries().size());
        assertEquals(LedgerTransactionType.TRANSFER, transaction.type());
    }

    @Test
    void balancesEachCurrencyIndependently() {
        assertThrows(IllegalArgumentException.class, () ->
            transaction(List.of(
                entry(EntryType.DEBIT, 1_000, BRL),
                entry(EntryType.CREDIT, 1_000, USD)
            ))
        );
    }

    @Test
    void acceptsMultipleEntriesWhenTotalsBalance() {
        LedgerTransaction transaction = transaction(List.of(
            entry(EntryType.DEBIT, 700, BRL),
            entry(EntryType.DEBIT, 300, BRL),
            entry(EntryType.CREDIT, 1_000, BRL)
        ));

        assertEquals(3, transaction.entries().size());
    }

    @Test
    void rejectsZeroValueEntry() {
        assertThrows(IllegalArgumentException.class, () ->
            entry(EntryType.DEBIT, 0, BRL)
        );
    }

    @Test
    void rejectsNegativeValueEntry() {
        assertThrows(IllegalArgumentException.class, () ->
            entry(EntryType.DEBIT, -1, BRL)
        );
    }

    @Test
    void rejectsTransactionWithFewerThanTwoEntries() {
        assertThrows(IllegalArgumentException.class, () ->
            transaction(List.of(entry(EntryType.DEBIT, 100, BRL)))
        );
    }

    @Test
    void rejectsFxConversionWithoutSnapshot() {
        assertThrows(IllegalArgumentException.class, () ->
            LedgerTransaction.create(
                LedgerTransactionType.FX_CONVERSION,
                null,
                null,
                null,
                List.of(
                    entry(EntryType.DEBIT, 100, BRL),
                    entry(EntryType.CREDIT, 100, BRL)
                )
            )
        );
    }

    @Test
    void rejectsReversalWithoutOriginalTransaction() {
        assertThrows(IllegalArgumentException.class, () ->
            LedgerTransaction.create(
                LedgerTransactionType.REVERSAL,
                null,
                null,
                null,
                List.of(
                    entry(EntryType.DEBIT, 100, BRL),
                    entry(EntryType.CREDIT, 100, BRL)
                )
            )
        );
    }

    @Test
    void rejectsTransactionReferencingItselfAsReversal() {
        LedgerTransactionId id = new LedgerTransactionId(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () ->
            new LedgerTransaction(
                id,
                LedgerTransactionType.REVERSAL,
                null,
                null,
                id,
                List.of(
                    entry(EntryType.DEBIT, 100, BRL),
                    entry(EntryType.CREDIT, 100, BRL)
                )
            )
        );
    }

    @Test
    void rejectsReferenceLongerThanDatabaseLimit() {
        String reference = "x".repeat(101);

        assertThrows(IllegalArgumentException.class, () ->
            LedgerTransaction.create(
                LedgerTransactionType.TRANSFER,
                reference,
                null,
                null,
                List.of(
                    entry(EntryType.DEBIT, 100, BRL),
                    entry(EntryType.CREDIT, 100, BRL)
                )
            )
        );
    }
}
