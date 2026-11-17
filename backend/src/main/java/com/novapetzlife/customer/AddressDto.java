package com.novapetzlife.customer;

import java.math.BigDecimal;

public record AddressDto(
        Long id,
        String recipientName,
        String phone,
        String addressLine1,
        String addressLine2,
        String landmark,
        String city,
        String state,
        String postalCode,
        BigDecimal latitude,
        BigDecimal longitude,
        Boolean isDefault
) {
    public static AddressDto fromEntity(CustomerAddress a) {
        return new AddressDto(
                a.getId(),
                a.getRecipientName(),
                a.getPhone(),
                a.getAddressLine1(),
                a.getAddressLine2(),
                a.getLandmark(),
                a.getCity(),
                a.getState(),
                a.getPostalCode(),
                a.getLatitude(),
                a.getLongitude(),
                a.getIsDefault()
        );
    }
}
