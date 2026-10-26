package dev.akira.payflow.transfer.application.port.out;

import java.util.Optional;

import dev.akira.payflow.account.domain.holder.AccountHolderId;
import dev.akira.payflow.ledger.domain.LedgerTransactionId;

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
