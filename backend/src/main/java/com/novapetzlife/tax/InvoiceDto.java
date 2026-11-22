package com.novapetzlife.tax;

import com.fasterxml.jackson.annotation.JsonProperty;

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
        BigDecimal discountAmount,
        BigDecimal deliveryCharge,
        BigDecimal grandTotal,
        String amountInWords,
        String documentType
) {
    @JsonProperty("finalPayableAmount")
    public BigDecimal getFinalPayableAmount() {
        return grandTotal;
    }

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
    ) {
        @JsonProperty("itemNumber")
        public int getItemNumber() {
            return itemNo;
        }

        @JsonProperty("taxableAmount")
        public BigDecimal getTaxableAmount() {
            return taxableValue;
        }

        @JsonProperty("grossTotal")
        public BigDecimal getGrossTotal() {
            return totalAmount;
        }

        @JsonProperty("returnPolicySummary")
        public String getReturnPolicySummary() {
            return returnPolicy;
        }
    }
}
