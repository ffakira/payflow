package dev.akira.payflow.auth.adapter.in.web;

import dev.akira.payflow.account.domain.holder.AccountHolderId;
import dev.akira.payflow.appuser.application.port.in.ProvisionAppUserCommand;
import dev.akira.payflow.appuser.application.port.in.ProvisionAppUserUseCase;
import dev.akira.payflow.auth.adapter.in.web.security.RegistrationForm;

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

import java.util.UUID;

@Controller
public class AuthPageController {

    private static final String LOGIN_PAGE = "auth/login";
    private static final String REGISTER_PAGE = "auth/register";

    private static final String REGISTRATION_FORM_FRAGMENT =
            "fragments/registration-form :: registration-form";

    private final ProvisionAppUserUseCase provisionAppUser;

    public AuthPageController(ProvisionAppUserUseCase provisionAppUser) {
        this.provisionAppUser = provisionAppUser;
    }

    @GetMapping("/login")
    public String login() {
        return LOGIN_PAGE;
    }

    @GetMapping("/register")
    public String register(Model model) {
        if (!model.containsAttribute("registrationForm")) {
            model.addAttribute("registrationForm", new RegistrationForm());
        }

        return REGISTER_PAGE;
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute("registrationForm") RegistrationForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            HttpServletResponse response,
            @RequestHeader(
                    value = "HX-Request",
                    required = false
            ) Boolean isHtmx
    ) {
        boolean htmxRequest = Boolean.TRUE.equals(isHtmx);

        if (!form.getPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue(
                    "confirmPassword",
                    "password.mismatch",
                    "Passwords do not match."
            );
        }

        if (bindingResult.hasErrors()) {
            return htmxRequest
                    ? REGISTRATION_FORM_FRAGMENT
                    : REGISTER_PAGE;
        }

        try {
            UUID holderUuid = UUID.fromString(form.getHolderId().strip());

            provisionAppUser.provision(new ProvisionAppUserCommand(
                new AccountHolderId(holderUuid),
                    form.getUsername(),
                    form.getPassword(),
                    form.getPin()
            ));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            bindingResult.reject(
                    "registration.failed",
                    "We couldn't create your login with those details. "
                        + "Check them or contact support."
            );

            return htmxRequest
                    ? REGISTRATION_FORM_FRAGMENT
                    :  REGISTER_PAGE;
        }

        if (htmxRequest) {
            response.setHeader("HX-Redirect", "/login?registered");
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);

            return null;
        }

        redirectAttributes.addFlashAttribute("registered", true);
        return "redirect:/login?registered";
    }
}
