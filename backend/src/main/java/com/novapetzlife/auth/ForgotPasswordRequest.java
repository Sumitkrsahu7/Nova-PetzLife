package com.novapetzlife.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email
) {
    public String getSanitizedEmail() {
        return email != null ? email.trim().toLowerCase() : "";
    }
}
