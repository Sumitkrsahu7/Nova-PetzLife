package com.novapetzlife.order;

import jakarta.validation.constraints.NotBlank;

public record OrderStatusUpdateRequest(
        @NotBlank(message = "New status is required")
        String newStatus,

        String reason,

        String deliveryOtp
) {
    public OrderStatusUpdateRequest(String newStatus, String reason) {
        this(newStatus, reason, null);
    }
}
