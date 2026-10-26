package dev.akira.payflow.account.application.port.out;

import dev.akira.payflow.account.domain.holder.AccountHolder;

public interface AccountHolderRepository {

    AccountHolder save(AccountHolder holder);
}
