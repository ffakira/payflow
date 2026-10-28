package dev.akira.payflow.appuser.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.akira.payflow.account.application.port.out.AccountHolderRepository;
import dev.akira.payflow.account.application.port.out.AccountRepository;
import dev.akira.payflow.account.domain.holder.AccountHolder;
import dev.akira.payflow.account.domain.holder.AccountHolderStatus;
import dev.akira.payflow.account.domain.holder.AccountHolderType;
import dev.akira.payflow.appuser.application.port.in.ProvisionAppUserCommand;
import dev.akira.payflow.appuser.application.port.in.ProvisionAppUserResult;
import dev.akira.payflow.appuser.application.port.in.ProvisionAppUserUseCase;
import dev.akira.payflow.appuser.application.port.out.AppUserRepository;
import dev.akira.payflow.appuser.application.port.out.CredentialHasher;
import dev.akira.payflow.appuser.domain.AppUser;
import dev.akira.payflow.appuser.domain.AppUserId;
import dev.akira.payflow.appuser.domain.AppUserStatus;

@Service
public class ProvisionAppUserService implements ProvisionAppUserUseCase {

    private final AccountHolderRepository accountHolderRepository;
    private final AccountRepository accountRepository;
    private final AppUserRepository appUserRepository;
    private final CredentialHasher credentialHasher;
    private final Clock clock;

    public ProvisionAppUserService(
            AccountHolderRepository accountHolderRepository,
            AccountRepository accountRepository,
            AppUserRepository appUserRepository,
            CredentialHasher credentialHasher,
            Clock clock
    ) {
        this.accountHolderRepository =
                Objects.requireNonNull(accountHolderRepository);
        this.accountRepository =
                Objects.requireNonNull(accountRepository);
        this.appUserRepository =
                Objects.requireNonNull(appUserRepository);
        this.credentialHasher =
                Objects.requireNonNull(credentialHasher);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional
    public ProvisionAppUserResult provision(
            ProvisionAppUserCommand command
    ) {
        Objects.requireNonNull(
                command,
                "Provisioning command cannot be null"
        );

        String username = normalizeUsername(command.username());

        AccountHolder holder = accountHolderRepository
                .findById(command.holderId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Account holder not found"
                        )
                );

        if (holder.type() != AccountHolderType.INDIVIDUAL
                || holder.status() != AccountHolderStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Account holder is not eligible for customer access"
            );
        }

        if (!accountRepository.existsByHolderId(holder.id())) {
            throw new IllegalStateException(
                    "Account holder has no eligible customer account"
            );
        }

        if (appUserRepository.findByHolderId(holder.id()).isPresent()) {
            throw new IllegalStateException(
                    "Account holder already has an application user"
            );
        }

        if (appUserRepository.findByUsername(username).isPresent()) {
            throw new IllegalStateException(
                    "Username is already taken"
            );
        }

        String passwordHash =
                credentialHasher.hashPassword(command.rawPassword());

        String pinHash =
                credentialHasher.hashPin(command.rawPin());

        Instant now = Instant.now(clock);

        AppUser user = new AppUser(
                AppUserId.generate(),
                holder.id(),
                username,
                passwordHash,
                pinHash,
                AppUserStatus.ACTIVE,
                0,
                null,
                now,
                now
        );

        AppUser saved = appUserRepository.save(user);

        return new ProvisionAppUserResult(
                saved.id(),
                saved.username()
        );
    }

    private static String normalizeUsername(String username) {
        Objects.requireNonNull(username, "Username cannot be null");

        String normalized = username.strip().toLowerCase(Locale.ROOT);

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(
                    "Username cannot be blank"
            );
        }

        if (normalized.length() > 100) {
            throw new IllegalArgumentException(
                    "Username cannot exceed 100 characters"
            );
        }

        return normalized;
    }
}
