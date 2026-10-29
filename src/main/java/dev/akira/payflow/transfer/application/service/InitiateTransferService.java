package dev.akira.payflow.transfer.application.service;

import dev.akira.payflow.account.application.port.out.AccountRepository;
import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.appuser.application.port.out.AppUserRepository;
import dev.akira.payflow.auth.application.port.in.VerifyTransactionPinCommand;
import dev.akira.payflow.auth.application.port.in.VerifyTransactionPinUseCase;
import dev.akira.payflow.common.error.BusinessException;
import dev.akira.payflow.common.error.ErrorCode;
import dev.akira.payflow.ledger.domain.LedgerTransaction;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class InitiateTransferService {

    private final AppUserRepository appUserRepository;
    private final AccountRepository accountRepository;
    private final VerifyTransactionPinUseCase pinVerification;
    private final TransferService transferService;

    public InitiateTransferService(
            AppUserRepository appUserRepository,
            AccountRepository accountRepository,
            VerifyTransactionPinUseCase pinVerification,
            TransferService transferService
    ) {
        this.appUserRepository = Objects.requireNonNull(appUserRepository);
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.pinVerification = Objects.requireNonNull(pinVerification);
        this.transferService = Objects.requireNonNull(transferService);
    }

    public LedgerTransaction execute(InitiateTransferCommand command) {
        Objects.requireNonNull(command, "Command must not be null");

        var appUser = appUserRepository.findByUsername(command.username())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACCOUNT_NOT_FOUND,
                        "User or source account not found"
                ));

        Account source = accountRepository.findById(command.sourceAccountId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACCOUNT_NOT_FOUND,
                        "User or source account not found"
                ));

        // Do not allow a caller to transfer from another user's account.
        // Use the same response as a missing source account.
        if (!source.holderId().equals(appUser.holderId())) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_NOT_FOUND,
                    "User or source account not found"
            );
        }

        boolean pinValid = pinVerification.verify(
               new VerifyTransactionPinCommand(
                       appUser.username(),
                       command.rawPin()
               )
        );

        if (!pinValid) {
            throw new BusinessException(
                    ErrorCode.INVALID_TRANSACTION_PIN,
                    "Transaction PIN is invalid or temporarily locked"
            );
        }

        return transferService.execute(
                new TransferCommand(
                        command.sourceAccountId(),
                        command.destinationAccountId(),
                        command.amount(),
                        command.idempotencyKey()
                )
        );
    }
}
