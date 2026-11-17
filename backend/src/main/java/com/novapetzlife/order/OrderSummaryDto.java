package com.novapetzlife.order;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryDto(
        Long id,
        String orderNumber,
        String customerName,
        String customerPhone,
        String orderStatus,
        String paymentStatus,
        String paymentMethod,
        BigDecimal finalPayableAmount,
        Integer totalItems,
        String deliverySlot,
        BigDecimal shopDistanceKm,
        Instant createdAt
) {
    public static OrderSummaryDto fromEntity(Order o) {
        String customerName = o.getCustomer() != null
                ? (o.getCustomer().getFirstName() + " " + (o.getCustomer().getLastName() != null ? o.getCustomer().getLastName() : "")).trim()
                : "Guest";

        int totalItems = o.getItems() != null
                ? o.getItems().stream().mapToInt(OrderItem::getQuantity).sum()
                : 0;

        return new OrderSummaryDto(
                o.getId(),
                o.getOrderNumber(),
                customerName,
                o.getCustomer() != null ? o.getCustomer().getPhone() : null,
                o.getOrderStatus(),
                o.getPaymentStatus(),
                o.getPaymentMethod(),
                o.getFinalPayableAmount(),
                totalItems,
                o.getDeliverySlot(),
                o.getShopDistanceKm(),
                o.getCreatedAt()
        );
    }
}
