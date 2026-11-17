package com.novapetzlife.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ProductCreateRequest(
        @NotBlank(message = "Product name is required")
        @Size(max = 255)
        String name,

        String slug,

        @NotNull(message = "Brand ID is required")
        Long brandId,

        @NotNull(message = "Category ID is required")
        Long categoryId,

        String description,
        String sku,
        String barcode,

        @NotNull(message = "Base price is required")
        @DecimalMin(value = "0.01", message = "Base price must be greater than zero")
        BigDecimal basePrice,

        BigDecimal discountedPrice,

        String petType,
        String lifeStage,
        String productType,

        Boolean isFeatured,
        Integer initialStock,

        List<String> imageUrls,
        List<VariantCreateRequest> variants,
        ReturnPolicyCreateRequest returnPolicy
) {
    public record VariantCreateRequest(
            @NotBlank String variantName,
            String sku,
            @NotNull BigDecimal price,
            BigDecimal discountedPrice,
            Integer weightGrams,
            Integer stock
    ) {}

    public record ReturnPolicyCreateRequest(
            Boolean returnAllowed,
            Integer returnWindowDays,
            Boolean originalPackagingRequired,
            Boolean unusedConditionRequired,
            Boolean refundAllowed,
            String refundMethod,
            String customConditions
    ) {}
}
