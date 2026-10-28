package dev.akira.payflow.sandbox.adapter.in.web;

import dev.akira.payflow.account.application.port.out.AccountRepository;
import dev.akira.payflow.account.application.service.ListAccountsService;
import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.common.domain.money.Money;
import dev.akira.payflow.common.error.BusinessException;
import dev.akira.payflow.sandbox.application.port.in.FundSandboxAccountUseCase;
import dev.akira.payflow.sandbox.application.service.FundSandboxAccountCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Controller
public class SandboxFundingController {

    private final ListAccountsService listAccountsService;
    private final AccountRepository accountRepository;
    private final FundSandboxAccountUseCase fundingUseCase;

    public SandboxFundingController(
            ListAccountsService listAccountsService,
            AccountRepository accountRepository,
            FundSandboxAccountUseCase fundingUseCase
    ) {
        this.listAccountsService = listAccountsService;
        this.accountRepository = accountRepository;
        this.fundingUseCase = fundingUseCase;
    }

    @GetMapping("/sandbox/funding")
    public String showFundingPage(Model model) {
        if (!model.containsAttribute("fundingForm")) {
            FundingForm form = new FundingForm();
            form.setIdempotencyKey(UUID.randomUUID().toString());
            model.addAttribute("fundingForm", form);
        }

        loadAccounts(model);
        return "sandbox/funding";
    }

    @PostMapping("/sandbox/funding")
    public String fundAccount(
            @Valid @ModelAttribute("fundingForm") FundingForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            loadAccounts(model);
            return "sandbox/funding";
        }

        try {
            AccountId accountId = AccountId.of(form.getAccountId());

            var account = accountRepository.findById(accountId)
                    .orElseThrow(() ->
                            new IllegalArgumentException("Selected account was not found")
                    );

            Money amount = Money.fromMajorUnits(
                    form.getAmount(), account.currency(), 2
            );

            fundingUseCase.execute(
                    new FundSandboxAccountCommand(accountId, amount, form.getIdempotencyKey())
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Funds added successfully."
            );

            return "redirect:/sandbox/funding";

        } catch (BusinessException | IllegalArgumentException exception) {
            bindingResult.reject(
                    "funding.failed",
                    exception.getMessage() == null
                    ? "Unable to add funds"
                    : exception.getMessage()
            );

            loadAccounts(model);
            return "sandbox/funding";
        }
    }

    private void loadAccounts(Model model) {
        model.addAttribute(
                "accounts",
                listAccountsService.execute(Optional.empty()).items()
        );
    }

    public static class FundingForm {

        @NotBlank(message = "Select an account.")
        private String accountId;

        @NotNull(message = "Enter an amount.")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
        @Digits(integer = 12, fraction = 2, message = "Use no more than 2 decimal places")
        private BigDecimal amount;

        @NotBlank(message = "Idempotency key is required")
        private String idempotencyKey;

        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getIdempotencyKey() {
            return idempotencyKey;
        }

        public void setIdempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
        }
    }
}
