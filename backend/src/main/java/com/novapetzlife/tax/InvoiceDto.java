package com.novapetzlife.tax;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoiceDto(
        // Store Details
        String storeName,
        String storeAddress,
        String storeCity,
        String storeState,
        String storeStateCode,
        String storePinCode,
        String storeGstin,
        String storePhone,

        // Invoice Meta
        String invoiceNumber,
        LocalDate invoiceDate,
        String orderNumber,
        String orderDate,
        String paymentMethod,
        String paymentStatus,
        String deliverySlot,
        String placeOfSupply,
        String reverseCharge,

        // Customer Details
        String customerName,
        String customerPhone,
        String customerEmail,
        String deliveryAddress,

        // Itemized Breakdown
        List<InvoiceItemDto> items,

        // Financial Totals
        BigDecimal totalTaxableValue,
        BigDecimal totalCgst,
        BigDecimal totalSgst,
        BigDecimal totalIgst,
        BigDecimal deliveryCharge,
        BigDecimal grandTotal,
        String amountInWords
) {
    public record InvoiceItemDto(
            int itemNo,
            String productName,
            String brandName,
            String sku,
            String hsnCode,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal taxableValue,
            BigDecimal cgstRate,
            BigDecimal cgstAmount,
            BigDecimal sgstRate,
            BigDecimal sgstAmount,
            BigDecimal totalAmount,
            String returnPolicy
    ) {}
}
