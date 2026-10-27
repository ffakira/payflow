package dev.akira.payflow.account.application.port.out;

import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.common.domain.validation.NotNull;

import java.time.Instant;

public record AccountCursor(@NotNull Instant createdAt, @NotNull AccountId id) {
}
