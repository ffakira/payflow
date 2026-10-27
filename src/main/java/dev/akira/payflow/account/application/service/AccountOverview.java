package dev.akira.payflow.account.application.service;

import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.account.domain.AccountBalance;
import dev.akira.payflow.common.domain.validation.NotNull;

public record AccountOverview(
        @NotNull(message = "Account must not be null")
        Account account,
        @NotNull(message = "Balance must not be null")
        AccountBalance balance
) {
    public AccountOverview {
        if (account != null && balance != null && !account.currency().equals(balance.currency())) {
            throw new IllegalArgumentException("Account and balance currencies must match");
        }
    }
}
