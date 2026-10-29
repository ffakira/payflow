package dev.akira.payflow.ledger.application.port.out;

import dev.akira.payflow.ledger.domain.LedgerTransaction;
import dev.akira.payflow.ledger.domain.LedgerTransactionId;

import java.util.Optional;

public interface LedgerTransactionRepository {

    LedgerTransaction save(LedgerTransaction transaction);

    Optional<LedgerTransaction> findById(LedgerTransactionId id);
}
