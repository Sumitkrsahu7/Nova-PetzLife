package com.novapetzlife.analytics.dto;

import java.math.BigDecimal;
import java.util.Map;

public class AnalyticsSummaryDto {

    private BigDecimal totalRevenue;
    private long totalOrders;
    private long deliveredOrders;
    private long cancelledOrders;
    private BigDecimal averageOrderValue;
    private BigDecimal totalRefunded;
    private long activeCouponsCount;
    private long lowStockCount;
    private long ordersWithin20kmCount;
    private Map<String, Long> ordersByStatus;
    private Map<String, Long> paymentMethodBreakdown;

    public AnalyticsSummaryDto() {}

    public AnalyticsSummaryDto(
            BigDecimal totalRevenue,
            long totalOrders,
            long deliveredOrders,
            long cancelledOrders,
            BigDecimal averageOrderValue,
            BigDecimal totalRefunded,
            long activeCouponsCount,
            long lowStockCount,
            long ordersWithin20kmCount,
            Map<String, Long> ordersByStatus,
            Map<String, Long> paymentMethodBreakdown) {
        this.totalRevenue = totalRevenue;
        this.totalOrders = totalOrders;
        this.deliveredOrders = deliveredOrders;
        this.cancelledOrders = cancelledOrders;
        this.averageOrderValue = averageOrderValue;
        this.totalRefunded = totalRefunded;
        this.activeCouponsCount = activeCouponsCount;
        this.lowStockCount = lowStockCount;
        this.ordersWithin20kmCount = ordersWithin20kmCount;
        this.ordersByStatus = ordersByStatus;
        this.paymentMethodBreakdown = paymentMethodBreakdown;
    }

    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
    public long getTotalOrders() { return totalOrders; }
    public void setTotalOrders(long totalOrders) { this.totalOrders = totalOrders; }
    public long getDeliveredOrders() { return deliveredOrders; }
    public void setDeliveredOrders(long deliveredOrders) { this.deliveredOrders = deliveredOrders; }
    public long getCancelledOrders() { return cancelledOrders; }
    public void setCancelledOrders(long cancelledOrders) { this.cancelledOrders = cancelledOrders; }
    public BigDecimal getAverageOrderValue() { return averageOrderValue; }
    public void setAverageOrderValue(BigDecimal averageOrderValue) { this.averageOrderValue = averageOrderValue; }
    public BigDecimal getTotalRefunded() { return totalRefunded; }
    public void setTotalRefunded(BigDecimal totalRefunded) { this.totalRefunded = totalRefunded; }
    public long getActiveCouponsCount() { return activeCouponsCount; }
    public void setActiveCouponsCount(long activeCouponsCount) { this.activeCouponsCount = activeCouponsCount; }
    public long getLowStockCount() { return lowStockCount; }
    public void setLowStockCount(long lowStockCount) { this.lowStockCount = lowStockCount; }
    public long getOrdersWithin20kmCount() { return ordersWithin20kmCount; }
    public void setOrdersWithin20kmCount(long ordersWithin20kmCount) { this.ordersWithin20kmCount = ordersWithin20kmCount; }
    public Map<String, Long> getOrdersByStatus() { return ordersByStatus; }
    public void setOrdersByStatus(Map<String, Long> ordersByStatus) { this.ordersByStatus = ordersByStatus; }
    public Map<String, Long> getPaymentMethodBreakdown() { return paymentMethodBreakdown; }
    public void setPaymentMethodBreakdown(Map<String, Long> paymentMethodBreakdown) { this.paymentMethodBreakdown = paymentMethodBreakdown; }
}
