package dev.akira.payflow.auth.domain;

import java.util.Objects;
import java.util.UUID;

public record AppUserId(UUID value) {

    public AppUserId {
        Objects.requireNonNull(value, "value cannot be null");
    }

    public static AppUserId generate() {
        return new AppUserId(UUID.randomUUID());
    }
}
