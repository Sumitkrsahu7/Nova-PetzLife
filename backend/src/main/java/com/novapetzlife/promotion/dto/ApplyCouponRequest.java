package com.novapetzlife.promotion.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class ApplyCouponRequest {

    @NotBlank(message = "Coupon code is required")
    private String code;

    @NotNull(message = "Subtotal is required")
    @DecimalMin(value = "0.00", message = "Subtotal cannot be negative")
    private BigDecimal cartSubtotal;

    private Long customerId;

    public ApplyCouponRequest() {}

    public ApplyCouponRequest(String code, BigDecimal cartSubtotal, Long customerId) {
        this.code = code;
        this.cartSubtotal = cartSubtotal;
        this.customerId = customerId;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public BigDecimal getCartSubtotal() { return cartSubtotal; }
    public void setCartSubtotal(BigDecimal cartSubtotal) { this.cartSubtotal = cartSubtotal; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
}
