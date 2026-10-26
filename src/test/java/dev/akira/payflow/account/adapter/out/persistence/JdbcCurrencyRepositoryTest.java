package dev.akira.payflow.account.adapter.out.persistence;

import dev.akira.payflow.common.domain.money.CurrencyCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JdbcCurrencyRepositoryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private JdbcCurrencyRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JdbcCurrencyRepository(jdbcTemplate);

        jdbcTemplate.update("""
                INSERT INTO currencies (code, minor_unit, enabled)
                VALUES ('EUR', 2, TRUE)
                ON CONFLICT (code) DO UPDATE SET enabled = TRUE
                """);

        jdbcTemplate.update("""
                INSERT INTO currencies (code, minor_unit, enabled)
                VALUES ('JPY', 0, FALSE)
                ON CONFLICT (code) DO UPDATE SET enabled = FALSE
                """);
    }

    @Test
    void returnsTrueForEnabledCurrency() {
        assertTrue(repository.isEnabled(CurrencyCode.of("EUR")));
    }

    @Test
    void returnsFalseForDisabledCurrency() {
        assertFalse(repository.isEnabled(CurrencyCode.of("JPY")));
    }

    @Test
    void returnsFalseForUnknownCurrency() {
        assertFalse(repository.isEnabled(CurrencyCode.of("GBP")));
    }
}
