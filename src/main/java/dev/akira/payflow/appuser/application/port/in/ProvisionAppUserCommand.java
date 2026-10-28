package dev.akira.payflow.appuser.application.port.in;

import dev.akira.payflow.account.domain.holder.AccountHolderId;

import java.util.Objects;

public record ProvisionAppUserCommand(
        AccountHolderId holderId,
        String username,
        String rawPassword,
        String rawPin
) {

    public ProvisionAppUserCommand {
        Objects.requireNonNull(holderId, "Holder ID cannot be nul");
        Objects.requireNonNull(username, "Username cannot be nul");
        Objects.requireNonNull(rawPassword, "Password cannot be nul");
        Objects.requireNonNull(rawPin, "Pin cannot be nul");

        username = username.strip();

        if (username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }

        if (rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password cannot be blank");
        }

        if (rawPin.isBlank()) {
            throw new IllegalArgumentException("Pin cannot be blank");
        }
    }
}
