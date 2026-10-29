package dev.akira.payflow.auth.adapter.out.security;

import dev.akira.payflow.appuser.application.port.out.CredentialHasher;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class SpringSecurityCredentialHasher implements CredentialHasher {

    private final PasswordEncoder passwordEncoder;

    public SpringSecurityCredentialHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "Password encoder cannot be null");
    }

    @Override
    public String hashPassword(String rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword cannot be null");

        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public String hashPin(String rawPin) {
        Objects.requireNonNull(rawPin, "rawPin cannot be null");

        return  passwordEncoder.encode(rawPin);
    }
}
