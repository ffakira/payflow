package dev.akira.payflow.account.application.service;

import dev.akira.payflow.account.application.port.out.AccountCursor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record AccountOverviewPage(
        List<AccountOverview> items,
        Optional<AccountCursor> nextCursor
) {
    public AccountOverviewPage {
        items = List.copyOf(Objects.requireNonNull(items));
        nextCursor = Objects.requireNonNull(nextCursor);
    }
}
