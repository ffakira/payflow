package dev.akira.payflow.account.application.service;

import dev.akira.payflow.account.domain.holder.AccountHolderType;
import dev.akira.payflow.common.domain.money.CurrencyCode;

public record OpenAccountCommand(
    AccountHolderType holderType,
    String displayName,
    CurrencyCode currency
) {
}
