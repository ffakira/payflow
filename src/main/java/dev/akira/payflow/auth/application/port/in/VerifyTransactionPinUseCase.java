package dev.akira.payflow.auth.application.port.in;

public interface VerifyTransactionPinUseCase {
    boolean verify(VerifyTransactionPinCommand command);
}
