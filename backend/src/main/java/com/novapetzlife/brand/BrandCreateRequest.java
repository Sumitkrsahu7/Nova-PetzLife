package com.novapetzlife.brand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BrandCreateRequest(
        @NotBlank(message = "Brand name is required")
        @Size(max = 150)
        String name,

        String slug,
        String description,
        String logoUrl,
        Boolean isActive
) {}
