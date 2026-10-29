package dev.akira.payflow.support.persistence;

import dev.akira.payflow.account.application.port.out.AccountCursor;
import dev.akira.payflow.account.application.port.out.AccountPage;
import dev.akira.payflow.account.application.port.out.AccountRepository;
import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.account.domain.AccountType;
import dev.akira.payflow.account.domain.holder.AccountHolderId;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryAccountRepository
        implements AccountRepository {

    private static final Comparator<Account> ACCOUNT_ORDER =
            Comparator.comparing(Account::createdAt)
                    .thenComparing(account -> account.id().value())
                    .reversed();

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

    @Override
    public AccountPage findCustomerAccounts(int pageSize, Optional<AccountCursor> after) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be greater than zero");
        }

        List<Account> matchingAccounts = accounts.values().stream()
                .filter(account ->
                        account.type() == AccountType.CUSTOMER
                )
                .sorted(ACCOUNT_ORDER)
                .filter(account -> after.isEmpty() || isAfterCursor(account, after.get()))
                .toList();

        List<Account> pageItems = matchingAccounts.stream()
                .limit((long) pageSize + 1)
                .toList();

        boolean hasNextPage = pageItems.size() > pageSize;

        List<Account> items = hasNextPage
                ? pageItems.subList(0, pageSize)
                : pageItems;

        Optional<AccountCursor> nextCursor = hasNextPage && !items.isEmpty()
                ? Optional.of(cursorFrom(items.getLast()))
                : Optional.empty();

        return new AccountPage(items, nextCursor);
    }

    @Override
    public boolean existsByHolderId(AccountHolderId holderId) {
        return accounts.values().stream()
                .anyMatch(account -> account.holderId().equals(holderId));
    }

    private static boolean isAfterCursor(Account account, AccountCursor cursor) {
        int createdComparison = account.createdAt().compareTo(cursor.createdAt());

        if (createdComparison != 0) {
            return createdComparison < 0;
        }

        return account.id().value().compareTo(cursor.id().value()) < 0;
    }

    private static AccountCursor cursorFrom(Account account) {
        return new AccountCursor(account.createdAt(), account.id());
    }

    public int size() {
        return accounts.size();
    }

    public boolean isEmpty() {
        return accounts.isEmpty();
    }
}
