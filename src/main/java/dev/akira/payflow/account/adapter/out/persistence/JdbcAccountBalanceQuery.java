package dev.akira.payflow.account.adapter.out.persistence;

import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.akira.payflow.account.application.port.out.AccountBalanceQuery;
import dev.akira.payflow.account.domain.AccountBalance;
import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.common.domain.money.CurrencyCode;

@Repository
public class JdbcAccountBalanceQuery implements AccountBalanceQuery {

    private static final String FIND_BY_ACCOUNT_ID_SQL = """
        SELECT
            a.currency_code,
            COALESCE(
                SUM(
                    CASE le.entry_type
                        WHEN 'CREDIT' THEN le.amount_minor
                        WHEN 'DEBIT' THEN -le.amount_minor
                    END
                ),
                0
            ) AS balance_minor
        FROM accounts a
        LEFT JOIN ledger_entries le
            ON le.account_id = a.id
        WHERE a.id = ?
        GROUP BY a.id, a.currency_code
    """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAccountBalanceQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public Optional<AccountBalance> findByAccountId(AccountId accountId) {
        Objects.requireNonNull(accountId, "Account ID must not be null");

        return jdbcTemplate.query(
            FIND_BY_ACCOUNT_ID_SQL,
            rs -> {
                if (!rs.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                    new AccountBalance(
                        rs.getLong("balance_minor"),
                        CurrencyCode.of(rs.getString("currency_code").trim())
                    )
                );
            },
            accountId.value()
        );
    }
}
