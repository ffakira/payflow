package dev.akira.payflow.account.application.service;

import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.account.domain.holder.AccountHolder;

public record OpenAccountResult(
    AccountHolder holder,
    Account account
) {
}
