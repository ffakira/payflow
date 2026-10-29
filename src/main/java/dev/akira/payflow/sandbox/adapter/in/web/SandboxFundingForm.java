package dev.akira.payflow.sandbox.adapter.in.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public class SandboxFundingForm {

    @NotBlank(message = "Select an account.")
    private String accountId;

    @NotNull(message = "Enter an amount.")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
    @Digits(integer = 12, fraction = 2, message = "Use no more than 2 decimal places.")
    private BigDecimal amount;

    @NotBlank(message = "Idempotency key is required")
    @Pattern(
            regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-"
                    + "[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                    + "[0-9a-fA-F]{12}$",
            message = "Invalid idempotency key."
    )
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
