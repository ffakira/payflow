package dev.akira.payflow.account.domain;

import dev.akira.payflow.common.domain.money.CurrencyCode;

import java.util.Objects;

public record AccountBalance(long minorUnits, CurrencyCode currency) {
    public AccountBalance {
        Objects.requireNonNull(currency, "Currency must not be null");
    }
}
