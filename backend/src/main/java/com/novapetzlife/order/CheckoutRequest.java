package com.novapetzlife.order;

import com.novapetzlife.customer.AddressCreateRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(
        Long cartId,
        String sessionToken,

        // Optional guest customer details
        String guestEmail,
        String guestPhone,
        String guestFirstName,
        String guestLastName,

        // Delivery address
        Long addressId,
        AddressCreateRequest address,

        // Coordinates for Haversine verification
        @NotNull(message = "Delivery destination latitude is required")
        Double latitude,

        @NotNull(message = "Delivery destination longitude is required")
        Double longitude,

        String deliverySlot,

        @NotBlank(message = "Payment method is required (e.g. COD, RAZORPAY)")
        String paymentMethod,

        String couponCode,

        String notes
) {
    public CheckoutRequest(
            Long cartId,
            String sessionToken,
            String guestEmail,
            String guestPhone,
            String guestFirstName,
            String guestLastName,
            Long addressId,
            AddressCreateRequest address,
            Double latitude,
            Double longitude,
            String deliverySlot,
            String paymentMethod,
            String notes) {
        this(cartId, sessionToken, guestEmail, guestPhone, guestFirstName, guestLastName, addressId, address, latitude, longitude, deliverySlot, paymentMethod, null, notes);
    }
}
