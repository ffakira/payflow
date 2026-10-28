package dev.akira.payflow.auth.application;

import java.time.Duration;
import java.util.Objects;

public record FailedLoginPolicy(int maxFailedAttempts, Duration lockDuration) {
    public FailedLoginPolicy {
        if (maxFailedAttempts < 1) {
            throw new IllegalArgumentException("Maximum failed attempts must be positive");
        }

        Objects.requireNonNull(lockDuration, "Lock duration cannot be null");

        if (lockDuration.isZero() || lockDuration.isNegative()) {
            throw new IllegalArgumentException("Lock duration must be positive");
        }
    }
}
