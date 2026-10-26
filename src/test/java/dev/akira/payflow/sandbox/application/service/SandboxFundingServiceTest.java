
package dev.akira.payflow.sandbox.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.akira.payflow.account.application.port.out.AccountRepository;
import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.account.domain.AccountStatus;
import dev.akira.payflow.account.domain.AccountType;
import dev.akira.payflow.account.domain.holder.AccountHolderId;
import dev.akira.payflow.common.domain.money.CurrencyCode;
import dev.akira.payflow.common.domain.money.Money;
import dev.akira.payflow.config.PayflowProperties;
import dev.akira.payflow.ledger.application.service.PostLedgerTransactionCommand;
import dev.akira.payflow.ledger.application.service.PostLedgerTransactionService;
import dev.akira.payflow.ledger.domain.EntryType;
import dev.akira.payflow.ledger.domain.LedgerEntry;
import dev.akira.payflow.ledger.domain.LedgerTransaction;
import dev.akira.payflow.ledger.domain.LedgerTransactionType;
import dev.akira.payflow.sandbox.application.port.out.SandboxClearingAccountProvider;

@ExtendWith(MockitoExtension.class)
class SandboxFundingServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SandboxClearingAccountProvider clearingAccountProvider;

    @Mock
    private PostLedgerTransactionService ledgerService;

    private SandboxFundingService service;

    private Account customerAccount;
    private Account clearingAccount;
    private Money amount;

    @BeforeEach
    void setUp() {
        service = new SandboxFundingService(
            new PayflowProperties("sandbox"),
            accountRepository,
            clearingAccountProvider,
            ledgerService
        );

        CurrencyCode brl = CurrencyCode.of("BRL");

        customerAccount = createAccount(
            AccountType.CUSTOMER,
            AccountStatus.ACTIVE,
            brl
        );

        clearingAccount = createAccount(
            AccountType.CLEARING,
            AccountStatus.ACTIVE,
            brl
        );

        amount = Money.ofMinorUnits(10_000, brl);
    }

    @Test
    void postsBalancedDepositForActiveCustomerAccount() {
        var command = new FundSandboxAccountCommand(
            customerAccount.id(),
            amount
        );

        LedgerTransaction expectedTransaction =
            org.mockito.Mockito.mock(LedgerTransaction.class);

        when(accountRepository.findById(customerAccount.id()))
            .thenReturn(Optional.of(customerAccount));
        when(clearingAccountProvider.getForCurrency(amount.currency()))
            .thenReturn(clearingAccount);
        when(ledgerService.execute(any(PostLedgerTransactionCommand.class)))
            .thenReturn(expectedTransaction);

        LedgerTransaction result = service.execute(command);

        assertSame(expectedTransaction, result);

        ArgumentCaptor<PostLedgerTransactionCommand> captor =
            ArgumentCaptor.forClass(PostLedgerTransactionCommand.class);

        verify(ledgerService).execute(captor.capture());

        PostLedgerTransactionCommand posted = captor.getValue();

        assertEquals(LedgerTransactionType.DEPOSIT, posted.type());
        assertEquals("SANDBOX_FUNDING", posted.reference());
        assertEquals(2, posted.entries().size());

        LedgerEntry debit = posted.entries().stream()
            .filter(entry -> entry.type() == EntryType.DEBIT)
            .findFirst()
            .orElseThrow();

        LedgerEntry credit = posted.entries().stream()
            .filter(entry -> entry.type() == EntryType.CREDIT)
            .findFirst()
            .orElseThrow();

        assertEquals(clearingAccount.id(), debit.accountId());
        assertEquals(customerAccount.id(), credit.accountId());
        assertEquals(amount, debit.amount());
        assertEquals(amount, credit.amount());
    }

    @Test
    void rejectsFundingWhenSandboxModeIsDisabled() {
        service = new SandboxFundingService(
            new PayflowProperties("production"),
            accountRepository,
            clearingAccountProvider,
            ledgerService
        );

        var command = new FundSandboxAccountCommand(
            customerAccount.id(),
            amount
        );

        assertThrows(
            IllegalStateException.class,
            () -> service.execute(command)
        );

        verify(accountRepository, never()).findById(any());
        verify(ledgerService, never()).execute(any());
    }

    @Test
    void rejectsUnknownCustomerAccount() {
        var command = new FundSandboxAccountCommand(
            customerAccount.id(),
            amount
        );

        when(accountRepository.findById(customerAccount.id()))
            .thenReturn(Optional.empty());

        assertThrows(
            IllegalArgumentException.class,
            () -> service.execute(command)
        );

        verify(ledgerService, never()).execute(any());
    }

    @Test
    void rejectsNonCustomerAccount() {
        Account feeAccount = createAccount(
            AccountType.FEE,
            AccountStatus.ACTIVE,
            amount.currency()
        );

        var command = new FundSandboxAccountCommand(
            feeAccount.id(),
            amount
        );

        when(accountRepository.findById(feeAccount.id()))
            .thenReturn(Optional.of(feeAccount));

        assertThrows(
            IllegalArgumentException.class,
            () -> service.execute(command)
        );

        verify(ledgerService, never()).execute(any());
    }

    @Test
    void rejectsInactiveCustomerAccount() {
        Account inactiveAccount = createAccount(
            AccountType.CUSTOMER,
            AccountStatus.CLOSED,
            amount.currency()
        );

        var command = new FundSandboxAccountCommand(
            inactiveAccount.id(),
            amount
        );

        when(accountRepository.findById(inactiveAccount.id()))
            .thenReturn(Optional.of(inactiveAccount));

        assertThrows(
            IllegalArgumentException.class,
            () -> service.execute(command)
        );

        verify(ledgerService, never()).execute(any());
    }

    @Test
    void rejectsAmountInDifferentCurrencyFromCustomerAccount() {
        Money usdAmount = Money.ofMinorUnits(
            1_000,
            CurrencyCode.of("USD")
        );

        var command = new FundSandboxAccountCommand(
            customerAccount.id(),
            usdAmount
        );

        when(accountRepository.findById(customerAccount.id()))
            .thenReturn(Optional.of(customerAccount));

        assertThrows(
            IllegalArgumentException.class,
            () -> service.execute(command)
        );

        verify(clearingAccountProvider, never()).getForCurrency(any());
        verify(ledgerService, never()).execute(any());
    }

    @Test
    void rejectsInactiveClearingAccount() {
        Account inactiveClearingAccount = createAccount(
            AccountType.CLEARING,
            AccountStatus.CLOSED,
            amount.currency()
        );

        var command = new FundSandboxAccountCommand(
            customerAccount.id(),
            amount
        );

        when(accountRepository.findById(customerAccount.id()))
            .thenReturn(Optional.of(customerAccount));
        when(clearingAccountProvider.getForCurrency(amount.currency()))
            .thenReturn(inactiveClearingAccount);

        assertThrows(
            IllegalArgumentException.class,
            () -> service.execute(command)
        );

        verify(ledgerService, never()).execute(any());
    }

    private Account createAccount(
        AccountType type,
        AccountStatus status,
        CurrencyCode currency
    ) {
        return new Account(
            AccountId.generate(),
            new AccountHolderId(UUID.randomUUID()),
            "PF" + UUID.randomUUID().toString().replace("-", ""),
            type,
            currency,
            status,
            Instant.now()
        );
    }
}
