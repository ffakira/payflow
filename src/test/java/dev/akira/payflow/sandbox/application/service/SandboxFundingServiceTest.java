
package dev.akira.payflow.sandbox.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import dev.akira.payflow.account.application.port.out.AccountRepository;
import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.account.domain.AccountStatus;
import dev.akira.payflow.account.domain.AccountType;
import dev.akira.payflow.account.domain.holder.AccountHolderId;
import dev.akira.payflow.common.domain.money.CurrencyCode;
import dev.akira.payflow.common.domain.money.Money;
import dev.akira.payflow.common.error.BusinessException;
import dev.akira.payflow.common.error.ErrorCode;
import dev.akira.payflow.config.PayflowProperties;
import dev.akira.payflow.ledger.application.port.out.LedgerTransactionRepository;
import dev.akira.payflow.ledger.application.service.PostLedgerTransactionCommand;
import dev.akira.payflow.ledger.application.service.PostLedgerTransactionService;
import dev.akira.payflow.ledger.domain.EntryType;
import dev.akira.payflow.ledger.domain.LedgerEntry;
import dev.akira.payflow.ledger.domain.LedgerTransaction;
import dev.akira.payflow.ledger.domain.LedgerTransactionId;
import dev.akira.payflow.ledger.domain.LedgerTransactionType;
import dev.akira.payflow.sandbox.application.port.out.SandboxClearingAccountProvider;
import dev.akira.payflow.transfer.application.port.out.TransferIdempotencyRecord;
import dev.akira.payflow.transfer.application.port.out.TransferIdempotencyRepository;

@ExtendWith(MockitoExtension.class)
class SandboxFundingServiceTest {

    private static final String OPERATION_TYPE = "SANDBOX_FUNDING";
    private static final String IDEMPOTENCY_KEY = "sandbox-funding-test-key";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SandboxClearingAccountProvider clearingAccountProvider;

    @Mock
    private PostLedgerTransactionService ledgerService;

    @Mock
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Mock
    private TransferIdempotencyRepository idempotencyRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private SandboxFundingService service;

    private Account customerAccount;
    private Account clearingAccount;
    private Money amount;

    @BeforeEach
    void setUp() {
        service = createService("sandbox");

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

        // Most tests exercise a first-time request.
        lenient().when(idempotencyRepository.find(
                any(AccountHolderId.class),
                anyString(),
                anyString()
        )).thenReturn(Optional.empty());
    }

    private SandboxFundingService createService(String mode) {
        return new SandboxFundingService(
                new PayflowProperties(mode),
                accountRepository,
                clearingAccountProvider,
                ledgerService,
                ledgerTransactionRepository,
                idempotencyRepository,
                transactionManager
        );
    }

    private FundSandboxAccountCommand command(
            Account account,
            Money fundingAmount,
            String key
    ) {
        return new FundSandboxAccountCommand(
                account.id(),
                fundingAmount,
                key
        );
    }

    @Test
    void postsBalancedDepositAndPersistsIdempotencyRecord() {
        FundSandboxAccountCommand command =
                command(customerAccount, amount, IDEMPOTENCY_KEY);

        LedgerTransaction expectedTransaction = transactionWithId(
                UUID.randomUUID()
        );

        givenValidFunding(expectedTransaction);

        LedgerTransaction result = service.execute(command);

        assertSame(expectedTransaction, result);

        ArgumentCaptor<PostLedgerTransactionCommand> ledgerCaptor =
                ArgumentCaptor.forClass(PostLedgerTransactionCommand.class);

        verify(ledgerService).execute(ledgerCaptor.capture());

        PostLedgerTransactionCommand posted = ledgerCaptor.getValue();

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

        LedgerTransactionId expectedTransactionId = expectedTransaction.id();

        verify(idempotencyRepository).save(
                eq(customerAccount.holderId()),
                eq(OPERATION_TYPE),
                eq(IDEMPOTENCY_KEY),
                eq(requestHash(command)),
                eq(expectedTransactionId)
        );
    }

    @Test
    void returnsOriginalTransactionWhenSameRequestIsRetried() {
        FundSandboxAccountCommand command =
                command(customerAccount, amount, IDEMPOTENCY_KEY);

        LedgerTransaction originalTransaction = transactionWithId(
                UUID.randomUUID()
        );

        TransferIdempotencyRecord existing = new TransferIdempotencyRecord(
                requestHash(command),
                originalTransaction.id()
        );

        when(accountRepository.findById(customerAccount.id()))
                .thenReturn(Optional.of(customerAccount));

        when(idempotencyRepository.find(
                customerAccount.holderId(),
                OPERATION_TYPE,
                IDEMPOTENCY_KEY
        )).thenReturn(Optional.of(existing));

        when(ledgerTransactionRepository.findById(
                originalTransaction.id()
        )).thenReturn(Optional.of(originalTransaction));

        LedgerTransaction result = service.execute(command);

        assertSame(originalTransaction, result);

        verify(ledgerService, never()).execute(any());
        verify(idempotencyRepository, never()).save(
                any(),
                anyString(),
                anyString(),
                anyString(),
                any()
        );
    }

    @Test
    void rejectsReusingKeyWithDifferentAmount() {
        FundSandboxAccountCommand originalCommand =
                command(customerAccount, amount, IDEMPOTENCY_KEY);

        Money differentAmount = Money.ofMinorUnits(
                20_000,
                amount.currency()
        );

        FundSandboxAccountCommand differentCommand =
                command(customerAccount, differentAmount, IDEMPOTENCY_KEY);

        TransferIdempotencyRecord existing = new TransferIdempotencyRecord(
                requestHash(originalCommand),
                new LedgerTransactionId(UUID.randomUUID())
        );

        when(accountRepository.findById(customerAccount.id()))
                .thenReturn(Optional.of(customerAccount));

        when(idempotencyRepository.find(
                customerAccount.holderId(),
                OPERATION_TYPE,
                IDEMPOTENCY_KEY
        )).thenReturn(Optional.of(existing));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.execute(differentCommand)
        );

        assertEquals(
                ErrorCode.IDEMPOTENCY_KEY_REUSED,
                exception.errorCode()
        );

        verify(ledgerService, never()).execute(any());
        verify(idempotencyRepository, never()).save(
                any(),
                anyString(),
                anyString(),
                anyString(),
                any()
        );
    }

    @Test
    void rejectsFundingWhenSandboxModeIsDisabled() {
        service = createService("production");

        FundSandboxAccountCommand command =
                command(customerAccount, amount, IDEMPOTENCY_KEY);

        assertThrows(
                IllegalStateException.class,
                () -> service.execute(command)
        );

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(clearingAccountProvider);
        verifyNoInteractions(ledgerService);
        verifyNoInteractions(idempotencyRepository);
    }

    @Test
    void rejectsUnknownCustomerAccount() {
        FundSandboxAccountCommand command =
                command(customerAccount, amount, IDEMPOTENCY_KEY);

        when(accountRepository.findById(customerAccount.id()))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.execute(command)
        );

        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, exception.errorCode());

        verify(ledgerService, never()).execute(any());
        verify(idempotencyRepository, never()).save(
                any(),
                anyString(),
                anyString(),
                anyString(),
                any()
        );
    }

    @Test
    void rejectsNonCustomerAccount() {
        Account feeAccount = createAccount(
                AccountType.FEE,
                AccountStatus.ACTIVE,
                amount.currency()
        );

        FundSandboxAccountCommand command =
                command(feeAccount, amount, IDEMPOTENCY_KEY);

        when(accountRepository.findById(feeAccount.id()))
                .thenReturn(Optional.of(feeAccount));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.execute(command)
        );

        assertEquals(
                ErrorCode.ACCOUNT_TYPE_NOT_ALLOWED,
                exception.errorCode()
        );

        verify(ledgerService, never()).execute(any());
        verify(idempotencyRepository, never()).save(
                any(),
                anyString(),
                anyString(),
                anyString(),
                any()
        );
    }

    @Test
    void rejectsInactiveCustomerAccount() {
        Account inactiveAccount = createAccount(
                AccountType.CUSTOMER,
                AccountStatus.CLOSED,
                amount.currency()
        );

        FundSandboxAccountCommand command =
                command(inactiveAccount, amount, IDEMPOTENCY_KEY);

        when(accountRepository.findById(inactiveAccount.id()))
                .thenReturn(Optional.of(inactiveAccount));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.execute(command)
        );

        assertEquals(ErrorCode.ACCOUNT_NOT_ACTIVE, exception.errorCode());

        verify(ledgerService, never()).execute(any());
        verify(idempotencyRepository, never()).save(
                any(),
                anyString(),
                anyString(),
                anyString(),
                any()
        );
    }

    @Test
    void rejectsAmountInDifferentCurrencyFromCustomerAccount() {
        Money usdAmount = Money.ofMinorUnits(
                1_000,
                CurrencyCode.of("USD")
        );

        FundSandboxAccountCommand command =
                command(customerAccount, usdAmount, IDEMPOTENCY_KEY);

        when(accountRepository.findById(customerAccount.id()))
                .thenReturn(Optional.of(customerAccount));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.execute(command)
        );

        assertEquals(ErrorCode.CURRENCY_MISMATCH, exception.errorCode());

        verify(clearingAccountProvider, never()).getForCurrency(any());
        verify(ledgerService, never()).execute(any());
        verify(idempotencyRepository, never()).save(
                any(),
                anyString(),
                anyString(),
                anyString(),
                any()
        );
    }

    @Test
    void rejectsInactiveClearingAccount() {
        Account inactiveClearingAccount = createAccount(
                AccountType.CLEARING,
                AccountStatus.CLOSED,
                amount.currency()
        );

        FundSandboxAccountCommand command =
                command(customerAccount, amount, IDEMPOTENCY_KEY);

        when(accountRepository.findById(customerAccount.id()))
                .thenReturn(Optional.of(customerAccount));

        when(clearingAccountProvider.getForCurrency(amount.currency()))
                .thenReturn(inactiveClearingAccount);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.execute(command)
        );

        assertEquals(ErrorCode.ACCOUNT_NOT_ACTIVE, exception.errorCode());

        verify(ledgerService, never()).execute(any());
        verify(idempotencyRepository, never()).save(
                any(),
                anyString(),
                anyString(),
                anyString(),
                any()
        );
    }

    @Test
    void rejectsInvalidClearingAccountConfiguration() {
        Account invalidClearingAccount = createAccount(
                AccountType.FEE,
                AccountStatus.ACTIVE,
                amount.currency()
        );

        FundSandboxAccountCommand command =
                command(customerAccount, amount, IDEMPOTENCY_KEY);

        when(accountRepository.findById(customerAccount.id()))
                .thenReturn(Optional.of(customerAccount));

        when(clearingAccountProvider.getForCurrency(amount.currency()))
                .thenReturn(invalidClearingAccount);

        assertThrows(
                IllegalStateException.class,
                () -> service.execute(command)
        );

        verify(ledgerService, never()).execute(any());
        verify(idempotencyRepository, never()).save(
                any(),
                anyString(),
                anyString(),
                anyString(),
                any()
        );
    }

    private void givenValidFunding(LedgerTransaction transaction) {
        when(accountRepository.findById(customerAccount.id()))
                .thenReturn(Optional.of(customerAccount));

        when(clearingAccountProvider.getForCurrency(amount.currency()))
                .thenReturn(clearingAccount);

        when(ledgerService.execute(any(PostLedgerTransactionCommand.class)))
                .thenReturn(transaction);
    }

    private LedgerTransaction transactionWithId(UUID id) {
        LedgerTransaction transaction =
                org.mockito.Mockito.mock(LedgerTransaction.class);

        when(transaction.id()).thenReturn(new LedgerTransactionId(id));

        return transaction;
    }

    private String requestHash(FundSandboxAccountCommand command) {
        String canonical = command.accountId().value()
                + "|" + command.amountMinor().minorUnits()
                + "|" + command.amountMinor().currency().value();

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new AssertionError("SHA-256 should be available", exception);
        }
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
