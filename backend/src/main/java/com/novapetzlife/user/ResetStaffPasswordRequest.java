package com.novapetzlife.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetStaffPasswordRequest(
        @NotBlank(message = "New password cannot be blank")
        @Size(min = 6, message = "Password must be at least 6 characters")
        String newPassword
) {}
