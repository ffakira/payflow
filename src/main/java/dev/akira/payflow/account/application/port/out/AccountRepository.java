package dev.akira.payflow.account.application.port.out;

import java.util.Optional;

import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.account.domain.AccountId;

public interface AccountRepository {
    
    Account save(Account account);

    Optional<Account> findById(AccountId id);

    Optional<Account> findByIdForUpdate(AccountId id);
}
