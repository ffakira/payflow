package dev.akira.payflow.appuser.application.port.in;

public interface ProvisionAppUserUseCase {
    ProvisionAppUserResult provision(ProvisionAppUserCommand command);
}
