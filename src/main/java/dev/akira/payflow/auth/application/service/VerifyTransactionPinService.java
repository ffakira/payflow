package dev.akira.payflow.auth.application.service;

import dev.akira.payflow.appuser.application.port.out.AppUserRepository;
import dev.akira.payflow.appuser.domain.AppUser;
import dev.akira.payflow.appuser.domain.AppUserStatus;
import dev.akira.payflow.auth.application.PinVerificationPolicy;
import dev.akira.payflow.auth.application.port.in.VerifyTransactionPinCommand;
import dev.akira.payflow.auth.application.port.in.VerifyTransactionPinUseCase;
import dev.akira.payflow.auth.application.port.out.PinVerifier;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Service
public final class VerifyTransactionPinService implements VerifyTransactionPinUseCase {

    private final AppUserRepository appUserRepository;
    private final PinVerifier pinVerifier;
    private final PinVerificationPolicy policy;
    private final Clock clock;

    public VerifyTransactionPinService(
            AppUserRepository appUserRepository,
            PinVerifier pinVerifier,
            PinVerificationPolicy policy,
            Clock clock
    ) {
        this.appUserRepository = Objects.requireNonNull(appUserRepository);
        this.pinVerifier = Objects.requireNonNull(pinVerifier);
        this.policy = Objects.requireNonNull(policy);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public boolean verify(VerifyTransactionPinCommand command) {
        Objects.requireNonNull(command, "Command cannot be null.");

        Optional<AppUser> optionalUser = appUserRepository.findByUsername(command.username());

        if (optionalUser.isEmpty()) {
            return false;
        }

        AppUser appUser = optionalUser.get();
        Instant now = Instant.now(clock);

        if (!canVerifyPin(appUser, now)) {
            return false;
        }

        if (appUserRepository.isPinVerificationLocked(appUser.username(), now)) {
            return false;
        }

        boolean matches = pinVerifier.matches(command.rawPin(), appUser.pinHash());

        if (!matches) {
            appUserRepository.recordFailedPinAttempts(
                    appUser.username(),
                    policy.maxFailedAttempts(),
                    now,
                    now.plus(policy.lockDuration())
            );

            return false;
        }

        // If a lock was applied concurrently, do not report success.
        return appUserRepository.resetFailedLoginAttempts(
                appUser.username(),
                now
        ) > 0;
    }

    private boolean canVerifyPin(AppUser appUser, Instant now) {
        if (appUser.status() == AppUserStatus.DISABLED) {
            return false;
        }

        if (appUser.status() == AppUserStatus.LOCKED) {
            return true;
        }

        // a locked account without an expiry remains locked
        if (appUser.lockedUntil() == null) {
            return false;
        }

        // If a lock was applied concurrently, do not report success.
        return appUserRepository.resetFailedLoginAttempts(
                appUser.username(),
                now
        ) > 0;
    }
}
