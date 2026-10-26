package dev.akira.payflow.transfer.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.akira.payflow.account.application.port.out.AccountBalanceQuery;
import dev.akira.payflow.account.application.port.out.AccountRepository;
import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.account.domain.AccountBalance;
import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.account.domain.AccountType;
import dev.akira.payflow.ledger.application.port.out.LedgerTransactionRepository;
import dev.akira.payflow.ledger.application.service.PostLedgerTransactionCommand;
import dev.akira.payflow.ledger.application.service.PostLedgerTransactionService;
import dev.akira.payflow.ledger.domain.EntryType;
import dev.akira.payflow.ledger.domain.LedgerEntry;
import dev.akira.payflow.ledger.domain.LedgerTransaction;
import dev.akira.payflow.ledger.domain.LedgerTransactionType;
import dev.akira.payflow.transfer.application.port.out.TransferIdempotencyRecord;
import dev.akira.payflow.transfer.application.port.out.TransferIdempotencyRepository;

@Service
public class TransferService {
    
    private static final String OPERATION_TYPE = "TRANSFER";
    private static final String REFERENCE = "INTERNAL_TRANSFER";

    private final AccountRepository accountRepository;
    private final AccountBalanceQuery accountBalanceQuery;
    private final PostLedgerTransactionService ledgerService;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final TransferIdempotencyRepository idempotencyRepository;
    private final TransactionTemplate transactionTemplate;

    public TransferService(
        AccountRepository accountRepository,
        AccountBalanceQuery accountBalanceQuery,
        PostLedgerTransactionService ledgerService,
        LedgerTransactionRepository ledgerTransactionRepository,
        TransferIdempotencyRepository idempotencyRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.accountBalanceQuery = Objects.requireNonNull(accountBalanceQuery);
        this.ledgerService = Objects.requireNonNull(ledgerService);
        this.ledgerTransactionRepository = Objects.requireNonNull(ledgerTransactionRepository);
        this.idempotencyRepository = Objects.requireNonNull(idempotencyRepository);
        this.transactionTemplate = new TransactionTemplate(
            Objects.requireNonNull(transactionManager)
        );
    }

    public LedgerTransaction execute(TransferCommand command) {
        Objects.requireNonNull(command, "Transfer command must not be null");
        String requestHash = hashRequest(command);

        try {
            return transactionTemplate.execute(status -> 
                executeWithinTransaction(command, requestHash)
            );
        } catch (DuplicateKeyException duplicate) {
            // The transaction template has rolled back the losing attempt before
            // this lookup. Only suppress the exception if the idempotency key won.
            Account source = accountRepository.findById(command.sourceAccountId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Source account not found: " + command.sourceAccountId().value()
                ));

            Optional<TransferIdempotencyRecord> existing = idempotencyRepository.find(
                source.holderId(),
                OPERATION_TYPE,
                command.idempotencyKey()
            );

            if (existing.isEmpty()) {
                throw duplicate;
            }

            return resolveExisting(existing.orElseThrow(), requestHash);
        }
    }

    private LedgerTransaction executeWithinTransaction(TransferCommand command, String requestHash) {
        Account sourceSnapshot = accountRepository.findById(command.sourceAccountId())
            .orElseThrow(() -> new IllegalArgumentException(
                "Source account not found: " + command.sourceAccountId().value()
            ));

        Optional<TransferIdempotencyRecord> existing = idempotencyRepository.find(
            sourceSnapshot.holderId(),
            OPERATION_TYPE,
            command.idempotencyKey()
        );

        if (existing.isPresent()) {
            return resolveExisting(existing.orElseThrow(), requestHash);
        }

        // lock both rows in stable order to avoid transfer-vs-transfer lock ivnersion.
        AccountId firstId = compareIds(command.sourceAccountId(), command.destinationAccountId()) <= 0
            ? command.sourceAccountId() : command.destinationAccountId();
        AccountId secondId = firstId.equals(command.sourceAccountId())
            ? command.destinationAccountId() : command.sourceAccountId();

        Account first = accountRepository.findByIdForUpdate(firstId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + firstId.value()));
        Account second = accountRepository.findByIdForUpdate(secondId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + secondId.value()));

        Account source = first.id().equals(command.sourceAccountId()) ? first : second;
        Account destination = first.id().equals(command.destinationAccountId()) ? first : second;

        validateAccounts(source, destination, command);

        AccountBalance balance = accountBalanceQuery.findByAccountId(source.id())
            .orElseThrow(() -> new IllegalStateException("Source account balance could not be loaded"));

        if (balance.minorUnits() < command.amount().minorUnits()) {
            throw new IllegalArgumentException("Insufficient funds");
        }

        LedgerTransaction transaction = ledgerService.execute(
            new PostLedgerTransactionCommand(
                LedgerTransactionType.TRANSFER,
                REFERENCE,
                null,
                null,
                List.of(
                    LedgerEntry.create(source.id(), EntryType.DEBIT, command.amount()),
                    LedgerEntry.create(destination.id(), EntryType.CREDIT, command.amount())
                )
            )
        );

        // Atomic with the ledger header and entries.
        idempotencyRepository.save(
            source.holderId(),
            OPERATION_TYPE,
            command.idempotencyKey(),
            requestHash,
            transaction.id()
        );

        return transaction;
    }

    private void validateAccounts(Account source, Account destination, TransferCommand command) {
        if (source.type() != AccountType.CUSTOMER || destination.type() != AccountType.CUSTOMER) {
            throw new IllegalArgumentException("Transfers require customer accounts");
        }

        if (!source.isActive() || !destination.isActive()) {
            throw new IllegalArgumentException("Both accounts must be active");
        }

        if (!source.currency().equals(destination.currency())) {
            throw new IllegalArgumentException("Transfer accounts must use the same currency");
        }

        if (!source.currency().equals(command.amount().currency())) {
            throw new IllegalArgumentException("Transfer amount currency must match the accounts");
        }
    }

    private LedgerTransaction resolveExisting(TransferIdempotencyRecord existing, String requestHash) {
        if (!existing.requestHash().equals(requestHash)) {
            throw new IllegalArgumentException(
                "Idempotency key has already been used with a different transfer request"
            );
        }

        return ledgerTransactionRepository.findById(existing.transactionId())
            .orElseThrow(() -> new IllegalStateException(
                "Idempotency record references a missing ledger transaction"
            ));
    }

    private int compareIds(AccountId left, AccountId right) {
        return left.value().compareTo(right.value());
    }

    private String hashRequest(TransferCommand command) {
        String canonical = command.sourceAccountId().value()
            + "|" + command.destinationAccountId().value()
            + "|" + command.amount().minorUnits()
            + "|" + command.amount().currency().value();

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
