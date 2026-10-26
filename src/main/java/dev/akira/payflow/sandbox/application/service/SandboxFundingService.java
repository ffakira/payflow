package dev.akira.payflow.sandbox.application.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.akira.payflow.account.application.port.out.AccountRepository;
import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.account.domain.AccountType;
import dev.akira.payflow.config.PayflowProperties;
import dev.akira.payflow.ledger.application.service.PostLedgerTransactionCommand;
import dev.akira.payflow.ledger.application.service.PostLedgerTransactionService;
import dev.akira.payflow.ledger.domain.EntryType;
import dev.akira.payflow.ledger.domain.LedgerEntry;
import dev.akira.payflow.ledger.domain.LedgerTransaction;
import dev.akira.payflow.ledger.domain.LedgerTransactionType;
import dev.akira.payflow.sandbox.application.port.in.FundSandboxAccountUseCase;
import dev.akira.payflow.sandbox.application.port.out.SandboxClearingAccountProvider;

@Service 
public class SandboxFundingService implements FundSandboxAccountUseCase {
    
    private final PayflowProperties properties;
    private final AccountRepository accountRepository;
    private final SandboxClearingAccountProvider clearingAccountProvider;
    private final PostLedgerTransactionService ledgerService;

    public SandboxFundingService(
        PayflowProperties properties,
        AccountRepository accountRepository,
        SandboxClearingAccountProvider clearingAccountProvider,
        PostLedgerTransactionService ledgerService
    ) {
        this.properties = Objects.requireNonNull(properties);
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.clearingAccountProvider = Objects.requireNonNull(clearingAccountProvider);
        this.ledgerService = Objects.requireNonNull(ledgerService);
    }

    @Override
    @Transactional
    public LedgerTransaction execute(FundSandboxAccountCommand command) {
        Objects.requireNonNull(command, "Command must not be null");

        if (!properties.isSandbox()) {
            throw new IllegalStateException(
                "Sandbox funding is disabled outside sandbox mode");
        }

        Account customerAccount = accountRepository
            .findById(command.accountId())
            .orElseThrow(() -> new IllegalArgumentException(
                "Customer account not found: "
                    + command.accountId().value()));

        if (customerAccount.type() != AccountType.CUSTOMER) {
            throw new IllegalArgumentException(
                "Only customer accounts can be funded");
        }

        if (!customerAccount.isActive()) {
            throw new IllegalArgumentException(
                "Customer account is not active");
        }

        if (!customerAccount.currency().equals(command.amountMinor().currency())) {
            throw new IllegalArgumentException(
                "Funding currency must match the account currency");
        }

        Account clearingAccount = clearingAccountProvider.getForCurrency(
            customerAccount.currency());

        if (clearingAccount == null
                || clearingAccount.type() != AccountType.CLEARING
                || !clearingAccount.currency().equals(customerAccount.currency())) {
            throw new IllegalStateException(
                "No valid clearing account configured for funding currency");
        }

        if (!clearingAccount.isActive()) {
            throw new IllegalArgumentException(
                "Clearing account is not active"
            );
        }

        List<LedgerEntry> entries = List.of(
            LedgerEntry.create(
                clearingAccount.id(),
                EntryType.DEBIT,
                command.amountMinor()
            ),
            LedgerEntry.create(
                customerAccount.id(),
                EntryType.CREDIT,
                command.amountMinor()
            )
        );

        PostLedgerTransactionCommand ledgerCommand = new PostLedgerTransactionCommand(
            LedgerTransactionType.DEPOSIT,
            "SANDBOX_FUNDING",
            null,
            null,
            entries);

        return ledgerService.execute(ledgerCommand);
    }
}
