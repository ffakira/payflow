package dev.akira.payflow.account.application.service;

import dev.akira.payflow.account.application.port.out.AccountBalanceQuery;
import dev.akira.payflow.account.domain.AccountBalance;
import dev.akira.payflow.account.domain.AccountId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
public class GetAccountBalanceService {

    private final AccountBalanceQuery accountBalanceQuery;

    public GetAccountBalanceService(
        AccountBalanceQuery accountBalanceQuery
    ) {
        this.accountBalanceQuery = Objects.requireNonNull(
            accountBalanceQuery,
            "Account balance query must not be null"
        );
    }

    @Transactional(readOnly = true)
    public Optional<AccountBalance> execute(AccountId accountId) {
        Objects.requireNonNull(
            accountId,
            "Account ID must not be null"
        );

        return accountBalanceQuery.findByAccountId(accountId);
    }
}
