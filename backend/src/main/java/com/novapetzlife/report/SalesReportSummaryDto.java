package com.novapetzlife.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SalesReportSummaryDto(
        String period,
        String queryDate,
        Instant startInstant,
        Instant endInstant,
        String formattedDateRange,
        long totalOrders,
        long successfulOrders,
        long pendingOrders,
        long cancelledOrders,
        long refundedOrders,
        long failedOrders,
        BigDecimal itemSubtotal,
        BigDecimal totalDiscounts,
        BigDecimal totalShipping,
        BigDecimal totalTax,
        BigDecimal customerReceipts,
        BigDecimal totalRefunds,
        BigDecimal netReceipts,
        long totalUnitsSold,
        BigDecimal averageOrderValue,
        List<SalesReportOrderDto> orders,
        long unattributableOrdersCount,
        BigDecimal unattributableAmount
) {
    /**
     * Backward-compatible constructor for existing tests and callers without unattributable metrics.
     */
    public SalesReportSummaryDto(
            String period,
            String queryDate,
            Instant startInstant,
            Instant endInstant,
            String formattedDateRange,
            long totalOrders,
            long successfulOrders,
            long pendingOrders,
            long cancelledOrders,
            long refundedOrders,
            long failedOrders,
            BigDecimal itemSubtotal,
            BigDecimal totalDiscounts,
            BigDecimal totalShipping,
            BigDecimal totalTax,
            BigDecimal customerReceipts,
            BigDecimal totalRefunds,
            BigDecimal netReceipts,
            long totalUnitsSold,
            BigDecimal averageOrderValue,
            List<SalesReportOrderDto> orders
    ) {
        this(period, queryDate, startInstant, endInstant, formattedDateRange,
                totalOrders, successfulOrders, pendingOrders, cancelledOrders, refundedOrders, failedOrders,
                itemSubtotal, totalDiscounts, totalShipping, totalTax,
                customerReceipts, totalRefunds, netReceipts, totalUnitsSold, averageOrderValue, orders,
                0L, BigDecimal.ZERO);
    }

    /**
     * Backward-compatible alias for customerReceipts (Paid Order Value).
     */
    public BigDecimal grossSales() {
        return customerReceipts;
    }

    /**
     * Backward-compatible alias for netReceipts (Customer Receipts - Completed Refunds).
     */
    public BigDecimal netSales() {
        return netReceipts;
    }
}
