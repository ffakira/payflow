
package dev.akira.payflow.config;

import dev.akira.payflow.auth.adapter.in.web.security.LoginFailureHandler;
import dev.akira.payflow.auth.adapter.in.web.security.LoginSuccessHandler;
import dev.akira.payflow.auth.adapter.out.security.AppUserDetailsService;
import dev.akira.payflow.auth.application.FailedLoginPolicy;
import dev.akira.payflow.auth.application.PinVerificationPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationProvider authenticationProvider,
            LoginFailureHandler loginFailureHandler,
            LoginSuccessHandler loginSuccessHandler
    ) throws Exception {

        http
                // csrf protection enabled for browser forms
                .csrf(Customizer.withDefaults())

                // define which pages are accessible without authentication
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/css/**",
                                "/fonts/**",
                                "/js/**",
                                "/actuator/health",
                                "/login",
                                "/register"
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                .authenticationProvider(authenticationProvider)

                // browser-based form login.
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(loginSuccessHandler)
                        .failureHandler(loginFailureHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    AuthenticationProvider authenticationProvider(
            AppUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }

    @Bean
    FailedLoginPolicy failedLoginPolicy() {
        return new FailedLoginPolicy(
                5,
                Duration.ofMinutes(15)
        );
    }

    @Bean
    PinVerificationPolicy pinVerificationPolicy() {
        return new PinVerificationPolicy(3, Duration.ofMinutes(15));
    }
}
