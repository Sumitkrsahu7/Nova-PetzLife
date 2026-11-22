package com.novapetzlife.report;

import java.math.BigDecimal;
import java.time.Instant;

public record SalesReportOrderDto(
        Long orderId,
        String orderNumber,
        Instant orderDate,
        String formattedDate,
        String customerName,
        String customerEmail,
        String customerPhone,
        String orderStatus,
        String paymentStatus,
        String paymentMethod,
        int itemsCount,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal shippingAmount,
        BigDecimal taxAmount,
        BigDecimal grandTotal,
        BigDecimal refundAmount,
        BigDecimal netAmount,
        String shippingCity,
        String shippingPincode,
        String itemsSummary
) {}
