package dev.akira.payflow.account.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.akira.payflow.account.application.port.out.AccountBalanceQuery;
import dev.akira.payflow.account.domain.AccountBalance;
import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.common.domain.money.CurrencyCode;

@ExtendWith(MockitoExtension.class)
class GetAccountBalanceServiceTest {
    
    @Mock
    private AccountBalanceQuery accountBalanceQuery;

    private GetAccountBalanceService service;

    @BeforeEach
    void setUp() {
        service = new GetAccountBalanceService(accountBalanceQuery);
    }

    @Test
    void returnsAccountBalanceWhenAccountExists() {
        AccountId accountId = new AccountId(UUID.randomUUID());
        AccountBalance expected = new AccountBalance(
            8_500,
            CurrencyCode.of("BRL")
        );

        when(accountBalanceQuery.findByAccountId(accountId))
            .thenReturn(Optional.of(expected));

        Optional<AccountBalance> result = service.execute(accountId);

        assertEquals(Optional.of(expected), result);
        verify(accountBalanceQuery).findByAccountId(accountId);
    }

    @Test
    void returnsEmptyWhenAccountDoesNotExist() {
        AccountId accountId = new AccountId(UUID.randomUUID());

        when(accountBalanceQuery.findByAccountId(accountId))
            .thenReturn(Optional.empty());

        Optional<AccountBalance> result = service.execute(accountId);

        assertEquals(Optional.empty(), result);
        verify(accountBalanceQuery).findByAccountId(accountId);
    }

    @Test
    void rejectsNullAccountId() {
        assertThrows(
            NullPointerException.class,
            () -> service.execute(null)
        );

        verifyNoInteractions(accountBalanceQuery);
    }
}
