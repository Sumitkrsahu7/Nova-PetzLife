package com.novapetzlife.delivery;

import java.math.BigDecimal;

public record AddressDeliveryCheckRequest(
        String fullAddress,
        String buildingOrHouse,
        String streetArea,
        String landmark,
        String city,
        String state,
        String pincode,
        Double latitude,
        Double longitude,
        BigDecimal orderSubtotal
) {}
