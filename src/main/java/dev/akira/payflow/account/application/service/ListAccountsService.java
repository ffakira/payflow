package dev.akira.payflow.account.application.service;

import dev.akira.payflow.account.application.port.out.AccountBalanceQuery;
import dev.akira.payflow.account.application.port.out.AccountRepository;
import dev.akira.payflow.account.domain.Account;
import dev.akira.payflow.account.domain.AccountBalance;
import dev.akira.payflow.account.domain.AccountType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class ListAccountsService {

    private final AccountRepository accountRepository;
    private final AccountBalanceQuery accountBalanceQuery;

    public ListAccountsService(AccountRepository accountRepository, AccountBalanceQuery accountBalanceQuery) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.accountBalanceQuery = Objects.requireNonNull(accountBalanceQuery);
    }

    @Transactional(readOnly = true)
    public List<AccountOverview> execute() {
        return accountRepository.findAll()
                .stream()
                .filter(account -> account.type() == AccountType.CUSTOMER)
                .map(this::toOverview)
                .toList();
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
