package com.novapetzlife.user;

import jakarta.validation.constraints.NotBlank;

public record UpdateStaffRequest(
        @NotBlank(message = "First name is required")
        String firstName,
        String lastName,
        String phone
) {}
