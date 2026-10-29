package dev.akira.payflow.auth.adapter.out.security;

import dev.akira.payflow.appuser.application.port.out.AppUserRepository;
import dev.akira.payflow.appuser.domain.AppUser;
import dev.akira.payflow.appuser.domain.AppUserStatus;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;
    private final Clock clock;

    public AppUserDetailsService(
            AppUserRepository appUserRepository,
            Clock clock
    ) {
        this.appUserRepository =
                Objects.requireNonNull(appUserRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        AppUser appUser = appUserRepository
                .findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Invalid username or password."
                        )
                );

        boolean disabled =
                appUser.status() == AppUserStatus.DISABLED;

        boolean locked = isLocked(appUser, Instant.now(clock));

        return User.withUsername(appUser.username())
                .password(appUser.passwordHash())
                .authorities("ROLE_USER")
                .accountLocked(locked)
                .disabled(disabled)
                .build();
    }

    private boolean isLocked(AppUser appUser, Instant now) {
        if (appUser.status() != AppUserStatus.LOCKED) {
            return false;
        }

        // A null lock expiry means the account remains locked
        // until an explicit administrative unlock.
        if (appUser.lockedUntil() == null) {
            return true;
        }

        return appUser.lockedUntil().isAfter(now);
    }
}
