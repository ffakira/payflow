package dev.akira.payflow.account.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.akira.payflow.account.application.port.out.AccountHolderRepository;
import dev.akira.payflow.account.application.port.out.AccountRepository;
import dev.akira.payflow.account.application.port.out.CurrencyRepository;
import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.account.domain.AccountType;
import dev.akira.payflow.account.domain.holder.AccountHolder;
import dev.akira.payflow.account.domain.holder.AccountHolderId;
import dev.akira.payflow.account.domain.holder.AccountHolderStatus;
import dev.akira.payflow.common.error.BusinessException;
import dev.akira.payflow.common.error.ErrorCode;

@Service
public class OpenAccountService {

    private final AccountHolderRepository holderRepository;
    private final AccountRepository accountRepository;
    private final CurrencyRepository currencyRepository;
    private final RandomAccountNumberGenerator randomAccountNumberGenerator;
    private final Clock clock;

    public OpenAccountService(
        AccountHolderRepository holderRepository,
        AccountRepository accountRepository,
        CurrencyRepository currencyRepository,
        RandomAccountNumberGenerator randomAccountNumberGenerator,
        Clock clock
    ) {
        this.holderRepository = Objects.requireNonNull(holderRepository);
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.currencyRepository = Objects.requireNonNull(currencyRepository);
        this.randomAccountNumberGenerator = Objects.requireNonNull(randomAccountNumberGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public OpenAccountResult execute(OpenAccountCommand command) {
        Objects.requireNonNull(command, "Command cannot be null");
        Objects.requireNonNull(command.holderType(), "Holder type cannot be null");
        Objects.requireNonNull(command.currency(), "Currency cannot be null");

        if (!currencyRepository.isEnabled(command.currency())) {
            throw new BusinessException(
                ErrorCode.CURRENCY_NOT_ENABLED,
                "Currency is not enabled: " + command.currency()
            );
        }

        if (command.displayName() == null || command.displayName().isBlank()) {
            throw new BusinessException(
                ErrorCode.DISPLAY_NAME_REQUIRED,
                "Display name cannot be blank"
            );
        }

        AccountHolderId holderId = AccountHolderId.generate();
        Instant now = clock.instant();

        AccountHolder holder = new AccountHolder(
            holderId,
            command.holderType(),
            command.displayName(),
            AccountHolderStatus.ACTIVE,
            now
        );

        AccountHolder savedHolder = holderRepository.save(holder);

        Account account = Account.open(
            AccountId.generate(),
            savedHolder.id(),
            randomAccountNumberGenerator.generate(),
            AccountType.CUSTOMER,
            command.currency(),
            now
        );

        Account savedAccount = accountRepository.save(account);
        return new OpenAccountResult(savedHolder, savedAccount);
    }
}
