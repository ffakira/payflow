
package dev.akira.payflow.support.persistence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.akira.payflow.ledger.application.port.out.LedgerTransactionRepository;
import dev.akira.payflow.ledger.domain.LedgerTransaction;
import dev.akira.payflow.ledger.domain.LedgerTransactionId;

public final class InMemoryLedgerTransactionRepository
        implements LedgerTransactionRepository {

    private final Map<LedgerTransactionId, LedgerTransaction>
        transactions = new LinkedHashMap<>();

    @Override
    public LedgerTransaction save(LedgerTransaction transaction) {
        transactions.put(transaction.id(), transaction);
        return transaction;
    }

    @Override
    public Optional<LedgerTransaction> findById(
            LedgerTransactionId id) {
        return Optional.ofNullable(transactions.get(id));
    }

    public List<LedgerTransaction> all() {
        return List.copyOf(transactions.values());
    }

    public int size() {
        return transactions.size();
    }

    public boolean isEmpty() {
        return transactions.isEmpty();
    }
}
