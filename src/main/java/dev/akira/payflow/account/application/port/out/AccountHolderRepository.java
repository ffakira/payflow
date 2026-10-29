package dev.akira.payflow.account.application.port.out;

import dev.akira.payflow.account.domain.holder.AccountHolder;
import dev.akira.payflow.account.domain.holder.AccountHolderId;

import java.util.Optional;

public interface AccountHolderRepository {

    AccountHolder save(AccountHolder holder);

    Optional<AccountHolder> findById(AccountHolderId id);
}
