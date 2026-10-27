package dev.akira.payflow.account.application.service;

import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.account.domain.AccountBalance;
import dev.akira.payflow.common.domain.validation.NotNull;
import dev.akira.payflow.common.error.BusinessException;
import dev.akira.payflow.common.error.ErrorCode;

public record AccountOverview(
        @NotNull(message = "Account must not be null")
        Account account,

        @NotNull(message = "Balance must not be null")
        AccountBalance balance
) {
    public AccountOverview {
        if (account != null && balance != null && !account.currency().equals(balance.currency())) {
            throw new BusinessException(
                    ErrorCode.CURRENCY_MISMATCH,
                    "Account and balance currencies must match"
            );
        }
    }
}
