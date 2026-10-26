package dev.akira.payflow.ledger.application.port.out;

import java.util.Optional;

import dev.akira.payflow.ledger.domain.LedgerTransaction;
import dev.akira.payflow.ledger.domain.LedgerTransactionId;

public interface LedgerTransactionRepository {

    LedgerTransaction save(LedgerTransaction transaction);

    Optional<LedgerTransaction> findById(LedgerTransactionId id);
}
