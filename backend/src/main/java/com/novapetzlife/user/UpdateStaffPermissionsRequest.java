package com.novapetzlife.user;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record UpdateStaffPermissionsRequest(
        @NotEmpty(message = "Permissions set cannot be empty")
        Set<String> permissions
) {}
