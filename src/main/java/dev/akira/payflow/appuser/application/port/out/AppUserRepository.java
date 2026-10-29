package dev.akira.payflow.appuser.application.port.out;

import dev.akira.payflow.account.domain.holder.AccountHolderId;
import dev.akira.payflow.appuser.domain.AppUser;

import java.time.Instant;
import java.util.Optional;

public interface AppUserRepository {

    AppUser save(AppUser user);

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByHolderId(AccountHolderId holderId);

    int recordFailedLoginAttempts(String username, int maxFailedAttempts, Instant now, Instant lockedUntil);

    int resetFailedLoginAttempts(String username, Instant now);

    boolean isPinVerificationLocked(String username, Instant now);

    int recordFailedPinAttempts(String username, int maxFailedAttempts, Instant now, Instant lockedUntil);

    int resetFailedPinAttempts(String username, Instant now);
}
