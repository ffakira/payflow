package dev.akira.payflow.account.application.service;

import dev.akira.payflow.account.application.port.out.AccountCursor;
import dev.akira.payflow.common.domain.validation.NotNull;

import java.util.List;
import java.util.Optional;

public record AccountOverviewPage(
        @NotNull(message = "Items must not be null")
        List<AccountOverview> items,

        @NotNull(message = "Next cursor must not be null")
        Optional<AccountCursor> nextCursor
) {
    public AccountOverviewPage {
        items = List.copyOf(items);
    }
}
