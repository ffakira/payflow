
package dev.akira.payflow.auth.adapter.in.web.security;

import dev.akira.payflow.appuser.application.port.out.AppUserRepository;
import dev.akira.payflow.auth.application.FailedLoginPolicy;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Component
public final class LoginFailureHandler
        extends SimpleUrlAuthenticationFailureHandler {

    private final AppUserRepository appUserRepository;
    private final FailedLoginPolicy failedLoginPolicy;
    private final Clock clock;

    public LoginFailureHandler(
            AppUserRepository appUserRepository,
            FailedLoginPolicy failedLoginPolicy,
            Clock clock
    ) {
        this.appUserRepository = Objects.requireNonNull(appUserRepository);
        this.failedLoginPolicy = Objects.requireNonNull(failedLoginPolicy);
        this.clock = Objects.requireNonNull(clock);

        setDefaultFailureUrl("/login?error");
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {

        String username = request.getParameter("username");

        if (username != null && !username.isBlank()) {
            String normalizedUsername =
                    username.strip().toLowerCase(Locale.ROOT);

            Instant now = Instant.now(clock);
            Instant lockedUntil = now.plus(failedLoginPolicy.lockDuration());

            appUserRepository.recordFailedLoginAttempts(
                    normalizedUsername,
                    failedLoginPolicy.maxFailedAttempts(),
                    now,
                    lockedUntil
            );
        }

        super.onAuthenticationFailure(request, response, exception);
    }
}
