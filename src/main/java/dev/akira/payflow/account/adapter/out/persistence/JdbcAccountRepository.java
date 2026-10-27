package dev.akira.payflow.account.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dev.akira.payflow.account.application.port.out.AccountCursor;
import dev.akira.payflow.account.application.port.out.AccountPage;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.akira.payflow.account.application.port.out.AccountRepository;
import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.account.domain.AccountStatus;
import dev.akira.payflow.account.domain.AccountType;
import dev.akira.payflow.account.domain.holder.AccountHolderId;
import dev.akira.payflow.common.domain.money.CurrencyCode;

@Repository
public class JdbcAccountRepository implements AccountRepository {

    private static final int MAX_PAGE_SIZE = 100;

    private static final String INSERT_SQL = """
        INSERT INTO accounts (
            id,
            holder_id,
            account_number,
            account_type,
            currency_code,
            status,
            created_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?)
    """;

    private static final String FIND_BY_ID_SQL = """
        SELECT
            id,
            holder_id,
            account_number,
            account_type,
            currency_code,
            status,
            created_at
        FROM accounts
        WHERE id = ?
    """;

    private static final String FIND_BY_ID_FOR_UPDATE_SQL = """
        SELECT
            id,
            holder_id,
            account_number,
            account_type,
            currency_code,
            status,
            created_at
        FROM accounts
        WHERE id = ?
        FOR UPDATE
    """;

    private static final String FIND_CUSTOMER_ACCOUNTS_SQL = """
        SELECT
            id,
            holder_id,
            account_number,
            account_type,
            currency_code,
            status,
            created_at
        FROM accounts
        WHERE account_type = 'CUSTOMER'
        ORDER BY created_at DESC, id DESC
        LIMIT ?
    """;

    private static final String FIND_CUSTOMER_ACCOUNTS_AFTER_SQL = """
        SELECT
            id,
            holder_id,
            account_number,
            account_type,
            currency_code,
            status,
            created_at
        FROM accounts
        WHERE account_type = 'CUSTOMER'
            AND (created_at, id) < (?, ?)
        ORDER BY created_at DESC, id DESC
        LIMIT ?
    """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public Account save(Account account) {
        Objects.requireNonNull(account, "Account cannot be null");

        jdbcTemplate.update(
            INSERT_SQL,
            account.id().value(),
            account.holderId().value(),
            account.accountNumber(),
            account.type().name(),
            account.currency().value(),
            account.status().name(),
            OffsetDateTime.ofInstant(
                account.createdAt(),
                ZoneOffset.UTC
            )
        );

        return account;
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        Objects.requireNonNull(id, "Account ID must not be null");

        try {
            return Optional.ofNullable(
                jdbcTemplate.queryForObject(
                    FIND_BY_ID_SQL,
                    (rs, rowNum) -> mapAccount(rs),
                    id.value()
                )
            );
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Account> findByIdForUpdate(AccountId id) {
        Objects.requireNonNull(id, "Account ID must not be null");

        try {
            return Optional.ofNullable(
                jdbcTemplate.queryForObject(
                    FIND_BY_ID_FOR_UPDATE_SQL,
                    (rs, rowNum) -> mapAccount(rs),
                    id.value()
                )
            );
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public AccountPage findCustomerAccounts(int pageSize, Optional<AccountCursor> after) {
        Objects.requireNonNull(after, "Cusor optional must not be null");

        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        int fetchLimit = pageSize + 1;

        List<Account> fetched = after
                .map(cursor -> jdbcTemplate.query(
                        FIND_CUSTOMER_ACCOUNTS_AFTER_SQL,
                        (rs, rowNum) -> mapAccount(rs),
                        OffsetDateTime.ofInstant(cursor.createdAt(), ZoneOffset.UTC),
                        cursor.id().value(),
                        fetchLimit
                ))
                .orElseGet(() -> jdbcTemplate.query(
                        FIND_CUSTOMER_ACCOUNTS_SQL,
                        (rs, rowNum) -> mapAccount(rs),
                        fetchLimit
                ));

        boolean hasNext = fetched.size() > pageSize;

        List<Account> items = new ArrayList<>(
                fetched.subList(0, Math.min(fetched.size(), pageSize))
        );

        Optional<AccountCursor> nextCursor = Optional.empty();

        if (hasNext && !items.isEmpty()) {
            Account last = items.getLast();

            nextCursor = Optional.of(
                    new AccountCursor(last.createdAt(), last.id())
            );
        }

        return new AccountPage(items, nextCursor);
    }

    private Account mapAccount(ResultSet rs) throws SQLException {
        return new Account(
            new AccountId(rs.getObject("id", java.util.UUID.class)),
            new AccountHolderId(
                rs.getObject("holder_id", java.util.UUID.class)
            ),
            rs.getString("account_number"),
            AccountType.valueOf(rs.getString("account_type")),
            CurrencyCode.of(rs.getString("currency_code").trim()),
            AccountStatus.valueOf(rs.getString("status")),
            rs.getObject("created_at", java.time.OffsetDateTime.class)
                .toInstant()
        );
    }
}
