package dev.akira.payflow.account.application.port.out;

import dev.akira.payflow.common.domain.money.CurrencyCode;

public interface CurrencyRepository {
    
    boolean isEnabled(CurrencyCode currency);
}
