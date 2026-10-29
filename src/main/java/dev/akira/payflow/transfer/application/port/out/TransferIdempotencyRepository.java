package dev.akira.payflow.transfer.application.port.out;

import dev.akira.payflow.account.domain.holder.AccountHolderId;
import dev.akira.payflow.ledger.domain.LedgerTransactionId;

import java.util.Optional;

public interface TransferIdempotencyRepository {
    
    Optional<TransferIdempotencyRecord> find(
        AccountHolderId holderId,
        String operationType,
        String idempotencyKey
    );

    void save(
        AccountHolderId holderId,
        String operationType,
        String idempotencyKey,
        String requestHash,
        LedgerTransactionId transactionId
    );
}
