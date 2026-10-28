package dev.akira.payflow.appuser.application.port.out;

public interface CredentialHasher {

    String hashPassword(String rawPassword);

    String hashPin(String rawPin);
}
