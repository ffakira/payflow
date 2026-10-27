package dev.akira.payflow.account.adapter.in.web;

import dev.akira.payflow.account.application.port.out.AccountCursor;
import dev.akira.payflow.account.application.service.ListAccountsService;
import dev.akira.payflow.account.domain.AccountId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/accounts")
public class AccountsController {

    private final ListAccountsService listAccountsService;

    public AccountsController(ListAccountsService listAccountsService) {
        this.listAccountsService = Objects.requireNonNull(listAccountsService);
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) Instant afterCreatedAt,
            @RequestParam(required = false) UUID afterId,
            Model model) {

        if ((afterCreatedAt == null) != (afterId == null)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Both cursor parameters are required"
            );
        }

        Optional<AccountCursor> cursor =
                afterCreatedAt == null
                ? Optional.empty()
                : Optional.of(new AccountCursor(afterCreatedAt, new AccountId(afterId)));

        model.addAttribute("page", listAccountsService.execute(cursor));
        return "accounts";
    }
}
