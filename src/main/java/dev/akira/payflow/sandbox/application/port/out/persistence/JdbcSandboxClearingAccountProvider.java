package dev.akira.payflow.sandbox.application.port.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import dev.akira.payflow.account.application.port.out.AccountRepository;
import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.common.domain.money.CurrencyCode;
import dev.akira.payflow.sandbox.application.port.out.SandboxClearingAccountProvider;

@Component
public class JdbcSandboxClearingAccountProvider implements SandboxClearingAccountProvider {

    private final JdbcTemplate jdbcTemplate;
    private final AccountRepository accountRepository;

    public JdbcSandboxClearingAccountProvider(JdbcTemplate jdbcTemplate, AccountRepository accountRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.accountRepository = accountRepository;
    }

    @Override
    public Account getForCurrency(CurrencyCode currency) {
        if (currency == null) {
            throw new NullPointerException("currency must not be null");
        }

        List<UUID> accountIds = jdbcTemplate.query(
            """
            SELECT a.id
            FROM accounts a
            JOIN account_holders h ON h.id = a.holder_id
            WHERE a.account_type = 'CLEARING'
                AND a.currency_code = ?
                AND a.status = 'ACTIVE'
                AND h.holder_type = 'SYSTEM'
                AND h.status = 'ACTIVE'
            """,
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            currency.value()
        );

        if (accountIds.isEmpty()) {
            throw new IllegalStateException(
                "No active system clearing account configured for currency "
                    + currency.value()
            );
        }

        if (accountIds.size() > 1) {
            throw new IllegalStateException(
                "Multiple active system clearing accounts configured for currency "
                    + currency.value()
            );
        }

        AccountId accountId = new AccountId(accountIds.getFirst());

        return accountRepository.findById(accountId)
            .orElseThrow(() -> new IllegalStateException(
                "Clearing account was found but could not be loaded: "
                    + accountId
            ));
    }
}
