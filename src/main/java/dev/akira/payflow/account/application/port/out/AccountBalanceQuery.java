package dev.akira.payflow.account.application.port.out;

import java.util.Optional;

import dev.akira.payflow.account.domain.AccountBalance;
import dev.akira.payflow.account.domain.AccountId;

public interface AccountBalanceQuery {
    
    Optional<AccountBalance> findByAccountId(AccountId accountId);
}
