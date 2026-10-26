package dev.akira.payflow.sandbox.application.port.in;

import dev.akira.payflow.ledger.domain.LedgerTransaction;
import dev.akira.payflow.sandbox.application.service.FundSandboxAccountCommand;

public interface FundSandboxAccountUseCase {

    LedgerTransaction execute(FundSandboxAccountCommand command);
}
