package com.novapetzlife.media.dto;

import jakarta.validation.constraints.NotBlank;

public record AddProductImageRequest(
        @NotBlank(message = "Image URL is required")
        String imageUrl,

        String altText,

        Integer displayOrder,

        Boolean isPrimary
) {}
