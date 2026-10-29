package dev.akira.payflow.sandbox.application.service;

import dev.akira.payflow.account.application.port.out.AccountRepository;
import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.account.domain.AccountType;
import dev.akira.payflow.common.error.BusinessException;
import dev.akira.payflow.common.error.ErrorCode;
import dev.akira.payflow.config.PayflowProperties;
import dev.akira.payflow.ledger.application.port.out.LedgerTransactionRepository;
import dev.akira.payflow.ledger.application.service.PostLedgerTransactionCommand;
import dev.akira.payflow.ledger.application.service.PostLedgerTransactionService;
import dev.akira.payflow.ledger.domain.EntryType;
import dev.akira.payflow.ledger.domain.LedgerEntry;
import dev.akira.payflow.ledger.domain.LedgerTransaction;
import dev.akira.payflow.ledger.domain.LedgerTransactionType;
import dev.akira.payflow.sandbox.application.port.in.FundSandboxAccountUseCase;
import dev.akira.payflow.sandbox.application.port.out.SandboxClearingAccountProvider;
import dev.akira.payflow.transfer.application.port.out.TransferIdempotencyRecord;
import dev.akira.payflow.transfer.application.port.out.TransferIdempotencyRepository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class SandboxFundingService implements FundSandboxAccountUseCase {

    private static final String OPERATION_TYPE = "SANDBOX_FUNDING";
    private static final String REFERENCE = "SANDBOX_FUNDING";

    private final PayflowProperties properties;
    private final AccountRepository accountRepository;
    private final SandboxClearingAccountProvider clearingAccountProvider;
    private final PostLedgerTransactionService ledgerService;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final TransferIdempotencyRepository idempotencyRepository;
    private final TransactionTemplate transactionTemplate;

    public SandboxFundingService(
            PayflowProperties properties,
            AccountRepository accountRepository,
            SandboxClearingAccountProvider clearingAccountProvider,
            PostLedgerTransactionService ledgerService,
            LedgerTransactionRepository ledgerTransactionRepository,
            TransferIdempotencyRepository idempotencyRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.properties = Objects.requireNonNull(properties);
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.clearingAccountProvider =
                Objects.requireNonNull(clearingAccountProvider);
        this.ledgerService = Objects.requireNonNull(ledgerService);
        this.ledgerTransactionRepository =
                Objects.requireNonNull(ledgerTransactionRepository);
        this.idempotencyRepository =
                Objects.requireNonNull(idempotencyRepository);
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager)
        );
    }

    @Override
    public LedgerTransaction execute(FundSandboxAccountCommand command) {
        Objects.requireNonNull(command, "Command must not be null");

        if (!properties.isSandbox()) {
            throw new IllegalStateException(
                    "Sandbox funding is disabled outside sandbox mode"
            );
        }

        String requestHash = hashRequest(command);

        try {
            return transactionTemplate.execute(status ->
                    executeWithinTransaction(command, requestHash)
            );
        } catch (DuplicateKeyException duplicate) {
            /*
             * The failed transaction has rolled back. If another concurrent
             * request saved this same key, return its transaction. Do not
             * suppress duplicate-key failures unrelated to this key.
             */
            Account customer = findAccount(command.accountId());

            Optional<TransferIdempotencyRecord> existing =
                    idempotencyRepository.find(
                            customer.holderId(),
                            OPERATION_TYPE,
                            command.idempotencyKey()
                    );

            if (existing.isEmpty()) {
                throw duplicate;
            }

            return resolveExisting(existing.orElseThrow(), requestHash);
        }
    }

    private LedgerTransaction executeWithinTransaction(
            FundSandboxAccountCommand command,
            String requestHash
    ) {
        Account customerAccount = findAccount(command.accountId());

        Optional<TransferIdempotencyRecord> existing =
                idempotencyRepository.find(
                        customerAccount.holderId(),
                        OPERATION_TYPE,
                        command.idempotencyKey()
                );

        if (existing.isPresent()) {
            return resolveExisting(existing.orElseThrow(), requestHash);
        }

        if (customerAccount.type() != AccountType.CUSTOMER) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_TYPE_NOT_ALLOWED,
                    "Only customer accounts can be funded"
            );
        }

        if (!customerAccount.isActive()) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_NOT_ACTIVE,
                    "Customer account is not active"
            );
        }

        if (!customerAccount.currency().equals(
                command.amountMinor().currency()
        )) {
            throw new BusinessException(
                    ErrorCode.CURRENCY_MISMATCH,
                    "Funding currency must match the account currency"
            );
        }

        Account clearingAccount = clearingAccountProvider.getForCurrency(
                customerAccount.currency()
        );

        if (clearingAccount == null
                || clearingAccount.type() != AccountType.CLEARING
                || !clearingAccount.currency().equals(
                customerAccount.currency()
        )) {
            throw new IllegalStateException(
                    "No valid clearing account configured for funding currency"
            );
        }

        if (!clearingAccount.isActive()) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_NOT_ACTIVE,
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

        LedgerTransaction transaction = ledgerService.execute(
                new PostLedgerTransactionCommand(
                        LedgerTransactionType.DEPOSIT,
                        REFERENCE,
                        null,
                        null,
                        entries
                )
        );

        /*
         * This insert and the ledger posting share the same transaction.
         * If either fails, the transaction rolls back.
         */
        idempotencyRepository.save(
                customerAccount.holderId(),
                OPERATION_TYPE,
                command.idempotencyKey(),
                requestHash,
                transaction.id()
        );

        return transaction;
    }

    private Account findAccount(AccountId accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACCOUNT_NOT_FOUND,
                        "Customer account not found: " + accountId.value()
                ));
    }

    private LedgerTransaction resolveExisting(
            TransferIdempotencyRecord existing,
            String requestHash
    ) {
        if (!existing.requestHash().equals(requestHash)) {
            throw new BusinessException(
                    ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "Idempotency key has already been used with a different funding request"
            );
        }

        return ledgerTransactionRepository.findById(existing.transactionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency record references a missing ledger transaction"
                ));
    }

    private String hashRequest(FundSandboxAccountCommand command) {
        String canonical = command.accountId().value()
                + "|" + command.amountMinor().minorUnits()
                + "|" + command.amountMinor().currency().value();

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(
                    "SHA-256 is not available",
                    impossible
            );
        }
    }
}
