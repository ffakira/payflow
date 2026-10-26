package dev.akira.payflow.ledger.domain;

public enum LedgerTransactionType {
    TRANSFER,
    DEPOSIT,
    WITHDRAWAL,
    FX_CONVERSION,
    FEE,
    ADJUSTMENT,
    REVERSAL
}
