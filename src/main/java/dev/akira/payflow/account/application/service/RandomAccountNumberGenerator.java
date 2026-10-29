package dev.akira.payflow.account.application.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class RandomAccountNumberGenerator {

    private static final int ACCOUNT_NUMBER_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generate() {
        StringBuilder accountNumber = new StringBuilder(ACCOUNT_NUMBER_LENGTH);

        for (int i = 0; i < ACCOUNT_NUMBER_LENGTH; i++) {
            accountNumber.append(RANDOM.nextInt(10));
        }

        return accountNumber.toString();
    }
}
