package dev.akira.payflow.auth.adapter.in.web.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegistrationForm {

    @NotBlank(message = "Enter your account holder ID")
    private String holderId = "";

    @NotBlank(message = "Choose a username.")
    @Size(min = 3, max = 100, message = "Username must be between 3 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Use letters, numbers, dots, underscores, or hyphens only.")
    private String username = "";

    @NotBlank(message = "Enter a password.")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    private String password = "";

    @NotBlank(message = "Confirm your password")
    private String confirmPassword = "";

    @NotBlank(message = "Choose a 6-digit transaction PIN.")
    @Pattern(regexp = "^[0-9]{6}$", message = "PIN must contain exactly 6 digits.")
    private String pin;

    public String getHolderId() {
        return holderId;
    }

    public void setHolderId(String holderId) {
        this.holderId = holderId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }
}
