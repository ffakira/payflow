package dev.akira.payflow.account.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import dev.akira.payflow.account.domain.holder.AccountHolderId;
import dev.akira.payflow.account.domain.holder.AccountHolderStatus;
import dev.akira.payflow.account.domain.holder.AccountHolderType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.akira.payflow.account.application.port.out.AccountHolderRepository;
import dev.akira.payflow.account.domain.holder.AccountHolder;

@Repository 
public class JdbcAccountHolderRepository implements AccountHolderRepository {

    private static final String INSERT_SQL = """
        INSERT INTO account_holders (
            id,
            holder_type,
            display_name,
            status,
            created_at
        ) VALUES (?, ?, ?, ?, ?)
    """;

    private static final String FIND_BY_ID_SQL = """
        SELECT
            id,
            holder_type,
            display_name,
            status,
            created_at
        FROM account_holders
        WHERE id = ?
    """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAccountHolderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public AccountHolder save(AccountHolder holder) {
        Objects.requireNonNull(holder, "Account holder cannot be null");

        jdbcTemplate.update(
            INSERT_SQL,
            holder.id().value(),
            holder.type().name(),
            holder.displayName(),
            holder.status().name(),
            OffsetDateTime.ofInstant(
                holder.createdAt(),
                ZoneOffset.UTC
            )
        );

        return holder;
    }

    @Override
    public Optional<AccountHolder> findById(AccountHolderId id) {
        Objects.requireNonNull(id, "Account holder id cannot be null");

        List<AccountHolder> results = jdbcTemplate.query(
                FIND_BY_ID_SQL,
                (rs, rowNum) -> mapAccountHolder(rs),
                id.value()
        );

        return results.stream().findFirst();
    }

    private AccountHolder mapAccountHolder(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);

        return new AccountHolder(
                new AccountHolderId(id),
                AccountHolderType.valueOf(rs.getString("holder_type")),
                rs.getString("display_name"),
                AccountHolderStatus.valueOf(rs.getString("status")),
                createdAt.toInstant()
        );
    }
}
