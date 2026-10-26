package dev.akira.payflow.sandbox.application.port.out;

import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.common.domain.money.CurrencyCode;

public interface SandboxClearingAccountProvider {
    
    Account getForCurrency(CurrencyCode currency);
}
