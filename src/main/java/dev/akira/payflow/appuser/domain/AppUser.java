package dev.akira.payflow.appuser.domain;

import dev.akira.payflow.account.domain.holder.AccountHolderId;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record AppUser(
        AppUserId id,
        AccountHolderId holderId,
        String username,
        String passwordHash,
        String pinHash,
        AppUserStatus status,
        int failedLoginAttempts,
        Instant lockedUntil,
        Instant createdAt,
        Instant updatedAt
) {
    public AppUser {
        Objects.requireNonNull(id, "App user id cannot be null");
        Objects.requireNonNull(holderId, "App user holder id cannot be null");
        Objects.requireNonNull(status, "App user status cannot be null");
        Objects.requireNonNull(createdAt, "App user createdAt cannot be null");
        Objects.requireNonNull(updatedAt, "App user updatedAt cannot be null");

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("App username cannot be null or blank");
        }

        username = username.strip().toLowerCase(Locale.ROOT);

        if (username.length() > 100) {
            throw new IllegalArgumentException("App username cannot be longer than 100 characters");
        }

        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("App password hash cannot be null or blank");
        }

        if (passwordHash.length() > 255) {
            throw new IllegalArgumentException("App password hash cannot be longer than 255 characters");
        }

        if (pinHash == null || pinHash.isBlank()) {
            throw new IllegalArgumentException("App pin hash cannot be null or blank");
        }

        if (pinHash.length() > 255) {
            throw new IllegalArgumentException("App pin hash cannot be longer than 255 characters");
        }

        if (failedLoginAttempts < 0) {
            throw new IllegalArgumentException("App failed login attempts cannot be negative");
        }
    }
}
