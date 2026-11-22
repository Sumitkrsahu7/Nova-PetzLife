package com.novapetzlife.auth;

import jakarta.validation.constraints.NotBlank;

public record GoogleAuthRequest(
        @NotBlank(message = "Google ID token credential is required")
        String credential
) {}
