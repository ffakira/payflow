
package dev.akira.payflow.account.application;

import dev.akira.payflow.account.application.service.OpenAccountCommand;
import dev.akira.payflow.account.application.service.OpenAccountResult;
import dev.akira.payflow.account.application.service.OpenAccountService;
import dev.akira.payflow.account.domain.holder.AccountHolderType;
import dev.akira.payflow.common.domain.money.CurrencyCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class OpenAccountIntegrationTest {

    @Autowired
    private OpenAccountService openAccountService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String displayName;

    @BeforeEach
    void setUp() {
        // The current schema limits display_name to 15 characters.
        displayName = "T" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 14);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                DELETE FROM accounts
                WHERE holder_id IN (
                    SELECT id
                    FROM account_holders
                    WHERE display_name = ?
                )
                """, displayName);

        jdbcTemplate.update("""
                DELETE FROM account_holders
                WHERE display_name = ?
                """, displayName);
    }

    @Test
    void opensAccountAndPersistsHolderAndAccount() {
        OpenAccountCommand command = new OpenAccountCommand(
                AccountHolderType.INDIVIDUAL,
                displayName,
                CurrencyCode.of("BRL")
        );

        OpenAccountResult result = openAccountService.execute(command);

        UUID holderId = result.holder().id().value();
        UUID accountId = result.account().id().value();

        Integer holderCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM account_holders
                WHERE id = ?
                """,
                Integer.class,
                holderId
        );

        Integer accountCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM accounts
                WHERE id = ?
                """,
                Integer.class,
                accountId
        );

        UUID persistedHolderId = jdbcTemplate.queryForObject(
                """
                SELECT holder_id
                FROM accounts
                WHERE id = ?
                """,
                UUID.class,
                accountId
        );

        String currencyCode = jdbcTemplate.queryForObject(
                """
                SELECT currency_code
                FROM accounts
                WHERE id = ?
                """,
                String.class,
                accountId
        );

        assertEquals(1, holderCount);
        assertEquals(1, accountCount);
        assertEquals(holderId, persistedHolderId);
        assertEquals("BRL", currencyCode);
        assertNotNull(result.account().accountNumber());
    }
}
