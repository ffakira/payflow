package dev.akira.payflow.auth.application.port.in;

import java.util.Locale;
import java.util.Objects;

public record VerifyTransactionPinCommand(String username, String rawPin) {

    public VerifyTransactionPinCommand {
        Objects.requireNonNull(username, "Username must not be null");
        Objects.requireNonNull(rawPin, "Raw pin must not be null");

        username = username.strip().toLowerCase(Locale.ROOT);

        if (username.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank");
        }

        if (rawPin.isBlank()) {
            throw new IllegalArgumentException("Raw pin must not be blank");
        }
    }
}
