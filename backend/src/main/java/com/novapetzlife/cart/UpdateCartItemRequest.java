package com.novapetzlife.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateCartItemRequest(
        @NotNull(message = "Quantity is required")
        @Min(value = 0, message = "Quantity must be non-negative (0 to remove)")
        Integer quantity
) {}
