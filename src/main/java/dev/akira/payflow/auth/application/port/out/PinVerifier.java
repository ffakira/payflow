package dev.akira.payflow.auth.application.port.out;

public interface PinVerifier {
    boolean matches(String rawPin, String encodedPin);
}
