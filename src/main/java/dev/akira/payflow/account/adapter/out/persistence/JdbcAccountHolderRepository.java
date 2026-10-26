package dev.akira.payflow.account.adapter.out.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

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
}
