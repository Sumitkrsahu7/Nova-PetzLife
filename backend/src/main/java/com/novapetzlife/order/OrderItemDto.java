package com.novapetzlife.order;

import java.math.BigDecimal;

public record OrderItemDto(
        Long id,
        Long productId,
        String productName,
        String brandName,
        String sku,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        String returnPolicySnapshot,
        String refundPolicySnapshot
) {
    public static OrderItemDto fromEntity(OrderItem i) {
        return new OrderItemDto(
                i.getId(),
                i.getProduct() != null ? i.getProduct().getId() : null,
                i.getProductName(),
                i.getBrandName(),
                i.getSku(),
                i.getQuantity(),
                i.getUnitPrice(),
                i.getTotalPrice(),
                i.getTaxRate(),
                i.getTaxAmount(),
                i.getReturnPolicySnapshot(),
                i.getRefundPolicySnapshot()
        );
    }
}
