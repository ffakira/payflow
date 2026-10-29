package dev.akira.payflow.account.application.service;

import dev.akira.payflow.account.application.port.out.AccountBalanceQuery;
import dev.akira.payflow.account.application.port.out.AccountCursor;
import dev.akira.payflow.account.application.port.out.AccountPage;
import dev.akira.payflow.account.application.port.out.AccountRepository;
import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.account.domain.AccountBalance;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
public class ListAccountsService {

    private static final int DEFAULT_PAGE_SIZE =  25;

    private final AccountRepository accountRepository;
    private final AccountBalanceQuery accountBalanceQuery;

    public ListAccountsService(AccountRepository accountRepository, AccountBalanceQuery accountBalanceQuery) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.accountBalanceQuery = Objects.requireNonNull(accountBalanceQuery);
    }

    @Transactional(readOnly = true)
    public AccountOverviewPage execute(Optional<AccountCursor> after) {
        AccountPage page = accountRepository.findCustomerAccounts(
                DEFAULT_PAGE_SIZE,
                Objects.requireNonNull(after)
        );

        return new AccountOverviewPage(
                page.items().stream()
                        .map(this::toOverview)
                        .toList(),
                page.nextCursor()
        );
    }

    private AccountOverview toOverview(Account account) {
        AccountBalance balance = accountBalanceQuery
                .findByAccountId(account.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Could not load balance for account "
                        + account.id().value()
                ));

        return new AccountOverview(account, balance);
    }
}
