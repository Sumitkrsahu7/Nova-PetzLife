package com.novapetzlife.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(
        @NotBlank(message = "Category name is required")
        @Size(max = 150)
        String name,

        String slug,
        String description,
        String imageUrl,
        Long parentId,
        Integer displayOrder
) {}
