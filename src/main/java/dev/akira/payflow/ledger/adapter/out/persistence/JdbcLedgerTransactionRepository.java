package dev.akira.payflow.ledger.adapter.out.persistence;

import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.common.domain.money.CurrencyCode;
import dev.akira.payflow.common.domain.money.Money;
import dev.akira.payflow.ledger.application.port.out.LedgerTransactionRepository;
import dev.akira.payflow.ledger.domain.EntryType;
import dev.akira.payflow.ledger.domain.LedgerEntry;
import dev.akira.payflow.ledger.domain.LedgerTransaction;
import dev.akira.payflow.ledger.domain.LedgerTransactionId;
import dev.akira.payflow.ledger.domain.LedgerTransactionType;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcLedgerTransactionRepository
        implements LedgerTransactionRepository {

    private static final String INSERT_TRANSACTION_SQL = """
            INSERT INTO ledger_transactions (
                id,
                transaction_type,
                reference,
                fx_rate_snapshot_id,
                reverses_transaction_id
            )
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String INSERT_ENTRY_SQL = """
            INSERT INTO ledger_entries (
                id,
                transaction_id,
                account_id,
                entry_type,
                amount_minor
            )
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String FIND_BY_ID_SQL = """
            SELECT
                lt.id AS transaction_id,
                lt.transaction_type,
                lt.reference,
                lt.fx_rate_snapshot_id,
                lt.reverses_transaction_id,
                le.id AS entry_id,
                le.account_id,
                le.entry_type,
                le.amount_minor,
                a.currency_code
            FROM ledger_transactions lt
            LEFT JOIN ledger_entries le
                ON le.transaction_id = lt.id
            LEFT JOIN accounts a
                ON a.id = le.account_id
            WHERE lt.id = ?
            ORDER BY le.created_at, le.id
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcLedgerTransactionRepository(
            JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    @Transactional
    public LedgerTransaction save(LedgerTransaction transaction) {
        Objects.requireNonNull(
                transaction,
                "Ledger transaction must not be null");

        jdbcTemplate.update(
                INSERT_TRANSACTION_SQL,
                transaction.id().value(),
                transaction.type().name(),
                transaction.reference(),
                transaction.fxRateSnapshotId(),
                transaction.reversesTransactionId() == null
                        ? null
                        : transaction.reversesTransactionId().value());

        jdbcTemplate.batchUpdate(
                INSERT_ENTRY_SQL,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(
                            java.sql.PreparedStatement statement,
                            int index) throws java.sql.SQLException {
                        LedgerEntry entry = transaction.entries().get(index);

                        statement.setObject(1, entry.id());
                        statement.setObject(
                                2,
                                transaction.id().value());
                        statement.setObject(
                                3,
                                entry.accountId().value());
                        statement.setString(
                                4,
                                entry.type().name());
                        statement.setLong(
                                5,
                                entry.amount().minorUnits());
                    }

                    @Override
                    public int getBatchSize() {
                        return transaction.entries().size();
                    }
                });

        return transaction;
    }

    @Override
    public Optional<LedgerTransaction> findById(LedgerTransactionId id) {
        Objects.requireNonNull(id, "id must not be null");

        List<LedgerTransactionRow> rows = jdbcTemplate.query(
                FIND_BY_ID_SQL,
                (rs, rowNum) -> new LedgerTransactionRow(
                        rs.getObject("transaction_id", UUID.class),
                        rs.getString("transaction_type"),
                        rs.getString("reference"),
                        rs.getObject("fx_rate_snapshot_id", UUID.class),
                        rs.getObject("reverses_transaction_id", UUID.class),
                        rs.getObject("entry_id", UUID.class),
                        rs.getObject("account_id", UUID.class),
                        rs.getString("entry_type"),
                        rs.getObject("amount_minor", Long.class),
                        rs.getString("currency_code")),
                id.value());

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        LedgerTransactionRow header = rows.getFirst();
        List<LedgerEntry> entries = rows.stream()
                .filter(row -> row.entryId() != null)
                .map(row -> new LedgerEntry(
                        row.entryId(),
                        new AccountId(row.accountId()),
                        EntryType.valueOf(row.entryType()),
                        Money.ofMinorUnits(
                                row.amountMinor(),
                                CurrencyCode.of(row.currencyCode().trim()))))
                .toList();

        return Optional.of(new LedgerTransaction(
                new LedgerTransactionId(header.transactionId()),
                LedgerTransactionType.valueOf(header.transactionType()),
                header.reference(),
                header.fxRateSnapshotId(),
                header.reversesTransactionId() == null
                        ? null
                        : new LedgerTransactionId(header.reversesTransactionId()),
                entries));
    }

    private record LedgerTransactionRow(
            UUID transactionId,
            String transactionType,
            String reference,
            UUID fxRateSnapshotId,
            UUID reversesTransactionId,
            UUID entryId,
            UUID accountId,
            String entryType,
            Long amountMinor,
            String currencyCode) {
    }
}
