package com.novapetzlife.refund;

import java.math.BigDecimal;
import java.time.Instant;

public class RefundDto {

    private Long id;
    private Long orderId;
    private String orderNumber;
    private String gatewayRefundId;
    private BigDecimal amount;
    private String status;
    private String reason;
    private String approvedBy;
    private Instant initiatedAt;
    private Instant completedAt;

    public RefundDto() {}

    public RefundDto(
            Long id,
            Long orderId,
            String orderNumber,
            String gatewayRefundId,
            BigDecimal amount,
            String status,
            String reason,
            String approvedBy,
            Instant initiatedAt,
            Instant completedAt) {
        this.id = id;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.gatewayRefundId = gatewayRefundId;
        this.amount = amount;
        this.status = status;
        this.reason = reason;
        this.approvedBy = approvedBy;
        this.initiatedAt = initiatedAt;
        this.completedAt = completedAt;
    }

    public static RefundDto fromEntity(Refund refund) {
        return new RefundDto(
                refund.getId(),
                refund.getOrder().getId(),
                refund.getOrder().getOrderNumber(),
                refund.getGatewayRefundId(),
                refund.getAmount(),
                refund.getStatus(),
                refund.getReason(),
                refund.getApprovedBy(),
                refund.getInitiatedAt(),
                refund.getCompletedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getGatewayRefundId() {
        return gatewayRefundId;
    }

    public void setGatewayRefundId(String gatewayRefundId) {
        this.gatewayRefundId = gatewayRefundId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getInitiatedAt() {
        return initiatedAt;
    }

    public void setInitiatedAt(Instant initiatedAt) {
        this.initiatedAt = initiatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
