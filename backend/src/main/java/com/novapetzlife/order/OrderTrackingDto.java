package com.novapetzlife.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderTrackingDto(
        String orderNumber,
        String orderStatus,
        String paymentStatus,
        String paymentMethod,
        String deliverySlot,
        BigDecimal shopDistanceKm,
        String deliveryAddressJson,
        Boolean isOtpVerified,
        Instant orderDate,
        List<OrderStatusHistoryDto> timeline
) {
    public static OrderTrackingDto fromEntity(Order o) {
        List<OrderStatusHistoryDto> timeline = o.getStatusHistory() != null
                ? o.getStatusHistory().stream().map(OrderStatusHistoryDto::fromEntity).toList()
                : List.of();

        return new OrderTrackingDto(
                o.getOrderNumber(),
                o.getOrderStatus(),
                o.getPaymentStatus(),
                o.getPaymentMethod(),
                o.getDeliverySlot(),
                o.getShopDistanceKm(),
                o.getDeliveryAddressJson(),
                Boolean.TRUE.equals(o.getIsOtpVerified()),
                o.getCreatedAt(),
                timeline
        );
    }
}
