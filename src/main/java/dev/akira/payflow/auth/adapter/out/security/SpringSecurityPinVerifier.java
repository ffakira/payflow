package dev.akira.payflow.auth.adapter.out.security;

import dev.akira.payflow.auth.application.port.out.PinVerifier;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class SpringSecurityPinVerifier implements PinVerifier {

    private final PasswordEncoder passwordEncoder;

    public SpringSecurityPinVerifier(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
    }

    @Override
    public boolean matches(String rawPin, String encodedPin) {
        Objects.requireNonNull(rawPin, "Raw PIN cannot be null");
        Objects.requireNonNull(encodedPin, "Encoded PIN cannot be null");

        return passwordEncoder.matches(rawPin, encodedPin);
    }
}
