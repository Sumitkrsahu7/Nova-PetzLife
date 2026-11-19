package com.novapetzlife.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CancelOrderItemRequest(
        @NotNull(message = "Order item ID is required")
        Long orderItemId,

        @NotBlank(message = "Cancellation reason is required")
        String reason
) {}
