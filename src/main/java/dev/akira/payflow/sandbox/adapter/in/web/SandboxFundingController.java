package dev.akira.payflow.sandbox.adapter.in.web;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import dev.akira.payflow.account.application.port.out.AccountRepository;
import dev.akira.payflow.account.application.service.ListAccountsService;
import dev.akira.payflow.account.domain.AccountId;
import dev.akira.payflow.common.domain.money.Money;
import dev.akira.payflow.common.error.BusinessException;
import dev.akira.payflow.sandbox.application.port.in.FundSandboxAccountUseCase;
import dev.akira.payflow.sandbox.application.service.FundSandboxAccountCommand;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Controller
public class SandboxFundingController {

    private static final String FUNDING_PAGE = "sandbox/funding";

    private static final String FUNDING_FORM_FRAGMENT =
            "fragments/funding-form :: funding-form";

    private final ListAccountsService listAccountsService;
    private final AccountRepository accountRepository;
    private final FundSandboxAccountUseCase fundingUseCase;
    private final JsonMapper jsonMapper;

    public SandboxFundingController(
            ListAccountsService listAccountsService,
            AccountRepository accountRepository,
            FundSandboxAccountUseCase fundingUseCase,
            JsonMapper jsonMapper
    ) {
        this.listAccountsService = listAccountsService;
        this.accountRepository = accountRepository;
        this.fundingUseCase = fundingUseCase;
        this.jsonMapper = jsonMapper;
    }

    @GetMapping("/sandbox/funding")
    public String showFundingPage(Model model) {
        if (!model.containsAttribute("fundingForm")) {
            SandboxFundingForm form = new SandboxFundingForm();
            form.setIdempotencyKey(UUID.randomUUID().toString());
            model.addAttribute("fundingForm", form);
        }

        loadAccounts(model);
        return FUNDING_PAGE;
    }

    @PostMapping("/sandbox/funding")
    public String fundAccount(
            @Valid @ModelAttribute("fundingForm") SandboxFundingForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes,
            HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false)
            Boolean isHtmx
    ) {
        boolean isHtmxRequest = Boolean.TRUE.equals(isHtmx);

        if (bindingResult.hasErrors()) {
            loadAccounts(model);
            return isHtmxRequest
                    ? FUNDING_FORM_FRAGMENT
                    : FUNDING_PAGE;
        }

        try {
            AccountId accountId = AccountId.of(form.getAccountId());

            var account = accountRepository.findById(accountId)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Selected account was not found."
                            )
                    );

            Money amount = Money.fromMajorUnits(
                    form.getAmount(),
                    account.currency(),
                    2
            );

            fundingUseCase.execute(
                    new FundSandboxAccountCommand(
                            accountId,
                            amount,
                            form.getIdempotencyKey()
                    )
            );

            if (isHtmxRequest) {
                SandboxFundingForm newForm = new SandboxFundingForm();
                newForm.setIdempotencyKey(UUID.randomUUID().toString());

                model.addAttribute("fundingForm", newForm);
                loadAccounts(model);

                setToastTrigger(
                        response,
                        "Funds added successfully!",
                        "success"
                );

                return FUNDING_FORM_FRAGMENT;
            }

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Funds added successfully."
            );

            return "redirect:/sandbox/funding";

        } catch (BusinessException | IllegalArgumentException exception) {
            String errorMessage = exception.getMessage() != null
                    ? exception.getMessage()
                    : "Unable to add funds.";

            bindingResult.reject("funding.failed", errorMessage);
            loadAccounts(model);

            if (isHtmxRequest) {
                setToastTrigger(response, errorMessage, "danger");
                return FUNDING_FORM_FRAGMENT;
            }

            return FUNDING_PAGE;
        }
    }

    private void loadAccounts(Model model) {
        model.addAttribute(
                "accounts",
                listAccountsService.execute(Optional.empty()).items()
        );
    }

    private void setToastTrigger(
            HttpServletResponse response,
            String message,
            String type
    ) {
        try {
            String payload = jsonMapper.writeValueAsString(
                    Map.of(
                            "showToast",
                            Map.of(
                                    "message", message,
                                    "type", type
                            )
                    )
            );

            response.setHeader("HX-Trigger", payload);

        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Failed to serialize HTMX trigger payload.",
                    exception
            );
        }
    }
}
