package com.novapetzlife.delivery;

import java.math.BigDecimal;

public record DeliveryEligibilityResponse(
        Boolean isEligible,
        BigDecimal distanceKm,
        BigDecimal deliveryRadiusKm,
        BigDecimal deliveryCharge,
        BigDecimal freeDeliveryThreshold,
        BigDecimal minOrderValue,
        Boolean isShopVerified,
        String shopName,
        String shopAddress,
        String message,
        BigDecimal estimatedRoadDistanceKm,
        Integer estimatedDurationMinutes
) {
    public DeliveryEligibilityResponse(
            Boolean isEligible,
            BigDecimal distanceKm,
            BigDecimal deliveryRadiusKm,
            BigDecimal deliveryCharge,
            BigDecimal freeDeliveryThreshold,
            BigDecimal minOrderValue,
            Boolean isShopVerified,
            String shopName,
            String shopAddress,
            String message
    ) {
        this(isEligible, distanceKm, deliveryRadiusKm, deliveryCharge, freeDeliveryThreshold, minOrderValue, isShopVerified, shopName, shopAddress, message, null, null);
    }
}
