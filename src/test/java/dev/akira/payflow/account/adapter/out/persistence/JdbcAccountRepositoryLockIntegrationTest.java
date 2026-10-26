package dev.akira.payflow.account.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.akira.payflow.account.application.port.out.AccountRepository;
import dev.akira.payflow.account.domain.AccountId;

@SpringBootTest
class JdbcAccountRepositoryLockIntegrationTest {
    
    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void findByIdForUpdateBlocksUntilTheFirstTransactionCompletes() throws Exception {
        UUID accountUuid = createAccount("BRL");
        AccountId accountId = new AccountId(accountUuid);

        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        CountDownLatch secondLockAcquired = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> firstTransaction = executor.submit(() ->
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        assertTrue(
                            accountRepository.findByIdForUpdate(accountId).isPresent(),
                            "The first transaction should find and lock the account"
                        );

                        firstLockAcquired.countDown();

                        try {
                            if (!releaseFirstTransaction.await(10, TimeUnit.SECONDS)) {
                                throw new AssertionError(
                                    "Timed out waiting to release the first transaction"
                                );
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(
                                "First transaction was interrupted",
                                exception
                            );
                        }
                    }
                )
            );

            assertTrue(
                firstLockAcquired.await(5, TimeUnit.SECONDS),
                "The first transaction should acquire the row lock"
            );

            Future<?> secondTransaction = executor.submit(() ->
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        assertTrue(
                            accountRepository.findByIdForUpdate(accountId).isPresent(),
                            "The second transaction should find the account"
                        );

                        secondLockAcquired.countDown();
                    })
            );

            assertFalse(
                secondLockAcquired.await(300, TimeUnit.MILLISECONDS),
                "The second transaction must wait while the first holds the row lock"
            );

            releaseFirstTransaction.countDown();

            firstTransaction.get(5, TimeUnit.SECONDS);
            secondTransaction.get(5, TimeUnit.SECONDS);

            assertTrue(
                secondLockAcquired.getCount() == 0,
                "The second transaction should acquire he lock after the first completes"
            );
        } finally {
            // always release the first transaction, even if an assertion fails.
            releaseFirstTransaction.countDown();
            executor.shutdown();
            assertTrue(
                executor.awaitTermination(5, TimeUnit.SECONDS),
                "Test executor should terminate"
            );
        }
    }

    private UUID createAccount(String currencyCode) {
        UUID holderId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        String displayName = "T" + UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 14);

        String accountNumber = "PF"
            + UUID.randomUUID().toString().replace("-", "");

        jdbcTemplate.update(
            """
            INSERT INTO account_holders (
                id,
                holder_type,
                display_name,
                status
            )
            VALUES (?, 'INDIVIDUAL', ?, 'ACTIVE')
            """,
            holderId,
            displayName
        );

        jdbcTemplate.update(
            """
            INSERT INTO accounts (
                id,
                holder_id,
                account_number,
                account_type,
                currency_code,
                status
            ) VALUES (?, ?, ?, 'CUSTOMER', ?, 'ACTIVE')
            """,
            accountId,
            holderId,
            accountNumber,
            currencyCode
        );

        return accountId;
    }
}
