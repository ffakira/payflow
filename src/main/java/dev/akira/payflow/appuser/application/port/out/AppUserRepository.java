package dev.akira.payflow.auth.appuser.out;

import dev.akira.payflow.account.domain.holder.AccountHolderId;
import dev.akira.payflow.appuser.domain.AppUser;

import java.util.Optional;

public interface AppUserRepository {

    AppUser save(AppUser user);

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByHolderId(AccountHolderId holderId);
}
