package com.novapetzlife.customer;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record AddressCreateRequest(
        @NotBlank(message = "Recipient name is required")
        String recipientName,

        @NotBlank(message = "Phone number is required")
        String phone,

        @NotBlank(message = "Address line 1 is required")
        String addressLine1,

        String addressLine2,
        String landmark,

        @NotBlank(message = "City is required")
        String city,

        @NotBlank(message = "State is required")
        String state,

        @NotBlank(message = "Postal code / PIN code is required")
        String postalCode,

        BigDecimal latitude,
        BigDecimal longitude,
        Boolean isDefault
) {}
