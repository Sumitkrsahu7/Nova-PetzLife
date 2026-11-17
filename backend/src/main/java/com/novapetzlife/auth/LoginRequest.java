package com.novapetzlife.auth;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
        @NotBlank(message = "Email or mobile number is required")
        @Pattern(
                regexp = "^(?:[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}|(?:\\+91)?[6-9]\\d{9})$",
                message = "Must be a valid email address or 10-digit Indian mobile number"
        )
        @JsonAlias({"identifier", "username", "phone"})
        String email,

        @NotBlank(message = "Password is required")
        String password
) {
    @JsonIgnore
    public String getIdentifier() {
        return email != null ? email.trim() : "";
    }
}


