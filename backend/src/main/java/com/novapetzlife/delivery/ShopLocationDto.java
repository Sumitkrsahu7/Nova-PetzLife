package com.novapetzlife.delivery;

import java.math.BigDecimal;
import java.time.Instant;

public record ShopLocationDto(
        Long id,
        String shopName,
        String addressLine,
        String city,
        String state,
        String country,
        String pinCode,
        String googlePlaceId,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal deliveryRadiusKm,
        String directionsUrl,
        Boolean isVerified,
        Instant verifiedAt
) {
    public static ShopLocationDto fromEntity(ShopLocation s) {
        return new ShopLocationDto(
                s.getId(),
                s.getShopName(),
                s.getAddressLine(),
                s.getCity(),
                s.getState(),
                s.getCountry(),
                s.getPinCode(),
                s.getGooglePlaceId(),
                s.getLatitude(),
                s.getLongitude(),
                s.getDeliveryRadiusKm(),
                s.getDirectionsUrl(),
                s.getIsVerified(),
                s.getVerifiedAt()
        );
    }
}
