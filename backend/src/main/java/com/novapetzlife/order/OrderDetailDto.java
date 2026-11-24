package com.novapetzlife.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderDetailDto(
        Long id,
        String orderNumber,
        Long customerId,
        String customerName,
        String customerEmail,
        String customerPhone,
        String orderStatus,
        String paymentStatus,
        String paymentMethod,
        BigDecimal totalAmount,
        BigDecimal discountAmount,
        BigDecimal deliveryCharge,
        BigDecimal taxAmount,
        BigDecimal finalPayableAmount,
        String deliveryAddressJson,
        BigDecimal shopDistanceKm,
        Boolean isWithinRadius,
        String deliverySlot,
        Boolean isOtpVerified,
        List<OrderItemDto> items,
        List<OrderStatusHistoryDto> statusHistory,
        Instant createdAt,
        Instant updatedAt,
        String gatewayPaymentId,
        String gatewayOrderId,
        List<com.novapetzlife.refund.RefundSummaryDto> refunds
) {
    public static OrderDetailDto fromEntity(Order o) {
        return fromEntity(o, null, null, List.of());
    }

    public static OrderDetailDto fromEntity(
            Order o,
            String gatewayPaymentId,
            String gatewayOrderId,
            List<com.novapetzlife.refund.RefundSummaryDto> refunds) {
        List<OrderItemDto> itemDtos = o.getItems() != null
                ? o.getItems().stream().map(OrderItemDto::fromEntity).toList()
                : List.of();

        List<OrderStatusHistoryDto> historyDtos = o.getStatusHistory() != null
                ? o.getStatusHistory().stream().map(OrderStatusHistoryDto::fromEntity).toList()
                : List.of();

        String customerName = o.getCustomer() != null
                ? (o.getCustomer().getFirstName() + " " + (o.getCustomer().getLastName() != null ? o.getCustomer().getLastName() : "")).trim()
                : "Guest Customer";

        return new OrderDetailDto(
                o.getId(),
                o.getOrderNumber(),
                o.getCustomer() != null ? o.getCustomer().getId() : null,
                customerName,
                o.getCustomer() != null ? o.getCustomer().getEmail() : null,
                o.getCustomer() != null ? o.getCustomer().getPhone() : null,
                o.getOrderStatus(),
                o.getPaymentStatus(),
                o.getPaymentMethod(),
                o.getTotalAmount(),
                o.getDiscountAmount(),
                o.getDeliveryCharge(),
                o.getTaxAmount(),
                o.getFinalPayableAmount(),
                o.getDeliveryAddressJson(),
                o.getShopDistanceKm(),
                o.getIsWithinRadius(),
                o.getDeliverySlot(),
                Boolean.TRUE.equals(o.getIsOtpVerified()),
                itemDtos,
                historyDtos,
                o.getCreatedAt(),
                o.getUpdatedAt(),
                gatewayPaymentId,
                gatewayOrderId,
                refunds != null ? refunds : List.of()
        );
    }
}
