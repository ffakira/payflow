package dev.akira.payflow.auth.adapter.in.web.security;

import dev.akira.payflow.appuser.application.port.out.AppUserRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Component
public final class LoginSuccessHandler
        extends SavedRequestAwareAuthenticationSuccessHandler {

    private final AppUserRepository appUserRepository;
    private final Clock clock;

    public LoginSuccessHandler(
            AppUserRepository appUserRepository,
            Clock clock
    ) {
        this.appUserRepository = Objects.requireNonNull(appUserRepository);
        this.clock = Objects.requireNonNull(clock);

        setDefaultTargetUrl("/dashboard");
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        String username = authentication.getName();

        if (username != null && !username.isBlank()) {
            appUserRepository.resetFailedLoginAttempts(
                    username.strip().toLowerCase(Locale.ROOT),
                    Instant.now(clock)
            );
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }
}
