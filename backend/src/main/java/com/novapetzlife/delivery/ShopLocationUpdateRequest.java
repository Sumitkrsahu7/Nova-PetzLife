package com.novapetzlife.delivery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record ShopLocationUpdateRequest(
        @NotBlank(message = "Shop name is mandatory") String shopName,
        @NotBlank(message = "Address line is mandatory") String addressLine,
        @NotBlank(message = "City is mandatory") String city,
        @NotBlank(message = "State is mandatory") String state,
        @NotBlank(message = "PIN code is mandatory")
        @Pattern(regexp = "^[1-9][0-9]{5}$", message = "PIN code must be a valid 6-digit Indian postal code")
        String pinCode,
        String googlePlaceId,
        @NotNull(message = "Latitude is mandatory") BigDecimal latitude,
        @NotNull(message = "Longitude is mandatory") BigDecimal longitude,
        @NotNull(message = "Delivery radius is mandatory") BigDecimal deliveryRadiusKm,
        String directionsUrl
) {}
