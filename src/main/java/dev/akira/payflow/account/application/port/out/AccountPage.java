package dev.akira.payflow.account.application.port.out;

import dev.akira.payflow.account.domain.Account;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record AccountPage(List<Account> items, Optional<AccountCursor> nextCursor) {
    public AccountPage {
        items = List.copyOf(Objects.requireNonNull(items));
        nextCursor = Objects.requireNonNull(nextCursor);
    }
}
