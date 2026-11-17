package com.novapetzlife.payment.dto;

import java.math.BigDecimal;

public class PaymentOrderResponse {

    private String razorpayOrderId;
    private Long amountInPaise;
    private BigDecimal amount;
    private String currency;
    private String keyId;
    private String orderNumber;
    private String customerName;
    private String customerEmail;
    private String customerPhone;

    public PaymentOrderResponse() {}

    public PaymentOrderResponse(
            String razorpayOrderId,
            Long amountInPaise,
            BigDecimal amount,
            String currency,
            String keyId,
            String orderNumber,
            String customerName,
            String customerEmail,
            String customerPhone) {
        this.razorpayOrderId = razorpayOrderId;
        this.amountInPaise = amountInPaise;
        this.amount = amount;
        this.currency = currency;
        this.keyId = keyId;
        this.orderNumber = orderNumber;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.customerPhone = customerPhone;
    }

    public String getRazorpayOrderId() {
        return razorpayOrderId;
    }

    public void setRazorpayOrderId(String razorpayOrderId) {
        this.razorpayOrderId = razorpayOrderId;
    }

    public Long getAmountInPaise() {
        return amountInPaise;
    }

    public void setAmountInPaise(Long amountInPaise) {
        this.amountInPaise = amountInPaise;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }
}
