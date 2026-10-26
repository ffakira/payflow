package dev.akira.payflow.account.adapter.out.persistence;

import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.akira.payflow.account.application.port.out.CurrencyRepository;
import dev.akira.payflow.common.domain.money.CurrencyCode;

@Repository
public class JdbcCurrencyRepository implements CurrencyRepository {
    
    private static final String EXISTS_ENABLED_CURRENCY_SQL = """
        SELECT EXISTS (
            SELECT 1
            FROM currencies
            WHERE code = ?
                AND enabled = TRUE
        )
    """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcCurrencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public boolean isEnabled(CurrencyCode currency) {
        Objects.requireNonNull(currency, "Currency cannot be null");

        Boolean enabled = jdbcTemplate.queryForObject(
            EXISTS_ENABLED_CURRENCY_SQL,
            Boolean.class,
            currency.value()
        );

        return Boolean.TRUE.equals(enabled);
    }
}
