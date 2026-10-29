package dev.akira.payflow.transfer.adapter.out;

import dev.akira.payflow.account.domain.holder.AccountHolderId;
import dev.akira.payflow.ledger.domain.LedgerTransactionId;
import dev.akira.payflow.transfer.application.port.out.TransferIdempotencyRecord;
import dev.akira.payflow.transfer.application.port.out.TransferIdempotencyRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcTransferIdempotencyRepository implements TransferIdempotencyRepository {
    
    private final JdbcTemplate jdbcTemplate;

    public JdbcTransferIdempotencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public Optional<TransferIdempotencyRecord> find(
        AccountHolderId holderId,
        String operationType,
        String idempotencyKey
    ) {
        Objects.requireNonNull(holderId, "Holder id must not be null");
        Objects.requireNonNull(operationType, "Operation type must not be null");
        Objects.requireNonNull(idempotencyKey, "Idempotency key must not be null");

        List<TransferIdempotencyRecord> records = jdbcTemplate.query(
            """
            SELECT request_hash, transaction_id
            FROM idempotency_requests
            WHERE holder_id = ?
                AND operation_type = ?
                AND idempotency_key = ?
            """,
            (rs, rowNum) -> new TransferIdempotencyRecord(
                rs.getString("request_hash").trim(),
                new LedgerTransactionId(rs.getObject("transaction_id", UUID.class))
            ),
            holderId.value(),
            operationType,
            idempotencyKey
        );

        return records.stream().findFirst();
    }

    @Override
    public void save(
        AccountHolderId holderId,
        String operationType,
        String idempotencyKey,
        String requestHash,
        LedgerTransactionId transactionId
    ) {
        Objects.requireNonNull(holderId, "Holder id must not be null");
        Objects.requireNonNull(operationType, "Operation type must not be null");
        Objects.requireNonNull(idempotencyKey, "Idempotency key must not be null");
        Objects.requireNonNull(requestHash, "Request hash must not be null");
        Objects.requireNonNull(transactionId, "Transaction id must not be null");

        jdbcTemplate.update(
            """
            INSERT INTO idempotency_requests (
                holder_id,
                operation_type,
                idempotency_key,
                request_hash,
                transaction_id
            ) VALUES (?, ?, ?, ?, ?)
            """,
            holderId.value(),
            operationType,
            idempotencyKey,
            requestHash,
            transactionId.value()
        );
    }
}
