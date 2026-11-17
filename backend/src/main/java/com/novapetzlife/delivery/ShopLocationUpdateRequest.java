package com.novapetzlife.delivery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ShopLocationUpdateRequest(
        @NotBlank String shopName,
        @NotBlank String addressLine,
        @NotBlank String city,
        @NotBlank String state,
        @NotBlank String pinCode,
        String googlePlaceId,
        @NotNull BigDecimal latitude,
        @NotNull BigDecimal longitude,
        @NotNull BigDecimal deliveryRadiusKm,
        String directionsUrl
) {}
