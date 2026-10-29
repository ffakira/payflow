package dev.akira.payflow.account.application.port.out;

import java.util.Optional;

import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.account.domain.holder.AccountHolderId;

public interface AccountRepository {
    
    Account save(Account account);

    Optional<Account> findById(AccountId id);

    Optional<Account> findByIdForUpdate(AccountId id);

    /**
     * Returns a page of customer accounts ordered by creation time, descending.
     *
     * @param pageSize maximum number of accounts to return
     * @param after optional cursor; empty when requesting he first page
     * @return the requested page and an optional cursor for the next page
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    AccountPage findCustomerAccounts(int pageSize, Optional<AccountCursor> after);

    boolean existsByHolderId(AccountHolderId holderId);
}
