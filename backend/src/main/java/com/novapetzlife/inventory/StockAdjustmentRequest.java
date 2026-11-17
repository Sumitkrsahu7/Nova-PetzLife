package com.novapetzlife.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StockAdjustmentRequest(
        @NotNull(message = "Product ID is required")
        Long productId,

        Long variantId,

        @NotNull(message = "Adjustment quantity is required")
        Integer quantityAdjustment,

        @NotBlank(message = "Adjustment reason is required")
        String reason
) {}
