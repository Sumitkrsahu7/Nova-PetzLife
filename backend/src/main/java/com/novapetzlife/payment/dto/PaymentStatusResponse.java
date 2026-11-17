package com.novapetzlife.payment.dto;

import java.math.BigDecimal;

public class PaymentStatusResponse {

    private String orderNumber;
    private String paymentStatus;
    private String orderStatus;
    private String transactionId;
    private BigDecimal amount;
    private boolean isSuccess;
    private String failureReason;

    public PaymentStatusResponse() {}

    public PaymentStatusResponse(
            String orderNumber,
            String paymentStatus,
            String orderStatus,
            String transactionId,
            BigDecimal amount,
            boolean isSuccess,
            String failureReason) {
        this.orderNumber = orderNumber;
        this.paymentStatus = paymentStatus;
        this.orderStatus = orderStatus;
        this.transactionId = transactionId;
        this.amount = amount;
        this.isSuccess = isSuccess;
        this.failureReason = failureReason;
    }

    public static PaymentStatusResponse success(String orderNumber, String orderStatus, String transactionId, BigDecimal amount) {
        return new PaymentStatusResponse(orderNumber, "COMPLETED", orderStatus, transactionId, amount, true, null);
    }

    public static PaymentStatusResponse failed(String orderNumber, String orderStatus, String reason, BigDecimal amount) {
        return new PaymentStatusResponse(orderNumber, "FAILED", orderStatus, null, amount, false, reason);
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public void setSuccess(boolean success) {
        isSuccess = success;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}
