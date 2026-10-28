package dev.akira.payflow.appuser.application.port.in;

import dev.akira.payflow.appuser.domain.AppUserId;

import java.util.Objects;

public record ProvisionAppUserResult(
        AppUserId id,
        String username
) {
    public ProvisionAppUserResult {
        Objects.requireNonNull(id, "App user ID cannot be null");
        Objects.requireNonNull(username, "Username cannot be null");
    }
}
