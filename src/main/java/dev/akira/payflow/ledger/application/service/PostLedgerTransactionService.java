package dev.akira.payflow.ledger.application.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.akira.payflow.account.application.port.out.AccountRepository;
import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.ledger.application.port.out.LedgerTransactionRepository;
import dev.akira.payflow.ledger.domain.LedgerEntry;
import dev.akira.payflow.ledger.domain.LedgerTransaction;

@Service 
public class PostLedgerTransactionService {
    
    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;

    public PostLedgerTransactionService(
        AccountRepository accountRepository,
        LedgerTransactionRepository ledgerTransactionRepository
    ) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.ledgerTransactionRepository = Objects.requireNonNull(ledgerTransactionRepository);
    }

    @Transactional
    public LedgerTransaction execute(PostLedgerTransactionCommand command) {
        Objects.requireNonNull(command, "Command must not be null");

        validateAccounts(command);

        LedgerTransaction transaction = LedgerTransaction.create(
            command.type(),
            command.reference(),
            command.fxRateSnapshotId(),
            command.reversesTransactionId(),
            command.entries()
        );

        return ledgerTransactionRepository.save(transaction);
    }

    private void validateAccounts(PostLedgerTransactionCommand command) {
        for (LedgerEntry entry : command.entries()) {
            Account account = accountRepository
                .findById(entry.accountId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Account not found: " + entry.accountId().value()
                ));

            if (!account.isActive()) {
                throw new IllegalArgumentException(
                    "Account is not active: " + account.id().value()
                );
            }

            if (!account.currency().equals(entry.amount().currency())) {
                throw new IllegalArgumentException(
                    "Entry currency does not match account currency: "
                        + account.id().value()
                );
            }
        }
    }
}
