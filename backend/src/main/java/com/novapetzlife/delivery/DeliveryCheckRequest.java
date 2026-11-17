package com.novapetzlife.delivery;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record DeliveryCheckRequest(
        @NotNull(message = "Destination latitude is required")
        Double latitude,

        @NotNull(message = "Destination longitude is required")
        Double longitude,

        String postalCode,

        BigDecimal orderSubtotal
) {}
