package dev.akira.payflow.sandbox.application.service;

import java.util.Objects;

import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.common.domain.money.Money;

public record FundSandboxAccountCommand(
    AccountId accountId,
    Money amountMinor
) {
    public FundSandboxAccountCommand {
        Objects.requireNonNull(accountId, "Account ID must not be null");
        Objects.requireNonNull(amountMinor, "Amount minor must not be null");

        if (!amountMinor.isPositive()) {
            throw new IllegalArgumentException(
                "Funding amount must be positive"
            );
        }
    }
}
