package dev.akira.payflow.account.domain;

import java.util.Objects;

import dev.akira.payflow.common.domain.money.CurrencyCode;

public record AccountBalance(long minorUnits, CurrencyCode currency) {
    public AccountBalance {
        Objects.requireNonNull(currency, "Currency must not be null");
    }
}
