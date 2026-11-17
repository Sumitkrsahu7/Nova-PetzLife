package com.novapetzlife.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Pattern(
                regexp = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}$",
                message = "Invalid email format"
        )
        String email,

        @NotBlank(message = "Password is required")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d\\s])\\S{8,}$",
                message = "Password must be at least 8 characters and include at least one uppercase letter, one lowercase letter, one number, and one special character"
        )
        String password,

        @NotBlank(message = "First name is required")
        String firstName,

        String lastName,

        @NotBlank(message = "Phone number is required")
        @Pattern(
                regexp = "^(?:\\+91)?[6-9]\\d{9}$",
                message = "Phone number must be a valid 10-digit Indian mobile number"
        )
        String phone
) {}

