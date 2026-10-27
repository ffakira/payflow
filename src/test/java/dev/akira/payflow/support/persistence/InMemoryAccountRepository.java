
package dev.akira.payflow.support.persistence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.akira.payflow.account.application.port.out.AccountRepository;
import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.account.domain.AccountId;

public final class InMemoryAccountRepository
        implements AccountRepository {

    private final Map<AccountId, Account> accounts =
        new LinkedHashMap<>();

    @Override
    public Account save(Account account) {
        accounts.put(account.id(), account);
        return account;
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return Optional.ofNullable(accounts.get(id));
    }

    @Override
    public Optional<Account> findByIdForUpdate(AccountId id) {
        // This fake does not provide database row locking.
        return findById(id);
    }

    public List<Account> all() {
        return List.copyOf(accounts.values());
    }

    public int size() {
        return accounts.size();
    }

    public boolean isEmpty() {
        return accounts.isEmpty();
    }
}
