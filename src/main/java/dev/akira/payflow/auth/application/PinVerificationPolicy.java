package dev.akira.payflow.auth.application;

import java.time.Duration;
import java.util.Objects;

public record PinVerificationPolicy(int maxFailedAttempts, Duration lockDuration) {
    public PinVerificationPolicy {
        if (maxFailedAttempts < 1) {
            throw new IllegalArgumentException("Maximum failed PIN attempts must positive");
        }

        Objects.requireNonNull(lockDuration, "PIN lock duration must not be null");

        if (lockDuration.isZero() || lockDuration.isNegative()) {
            throw new IllegalArgumentException("PIN lock duration must be positive");
        }
    }
}
