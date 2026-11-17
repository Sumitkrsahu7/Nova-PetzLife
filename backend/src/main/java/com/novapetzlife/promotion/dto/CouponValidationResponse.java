package com.novapetzlife.promotion.dto;

import java.math.BigDecimal;

public class CouponValidationResponse {

    private boolean valid;
    private String code;
    private String discountType;
    private BigDecimal discountAmount;
    private BigDecimal originalSubtotal;
    private BigDecimal newSubtotal;
    private String message;

    public CouponValidationResponse() {}

    public static CouponValidationResponse success(
            String code,
            String discountType,
            BigDecimal discountAmount,
            BigDecimal originalSubtotal,
            BigDecimal newSubtotal,
            String message) {
        CouponValidationResponse res = new CouponValidationResponse();
        res.setValid(true);
        res.setCode(code);
        res.setDiscountType(discountType);
        res.setDiscountAmount(discountAmount);
        res.setOriginalSubtotal(originalSubtotal);
        res.setNewSubtotal(newSubtotal);
        res.setMessage(message);
        return res;
    }

    public static CouponValidationResponse invalid(String code, String message, BigDecimal originalSubtotal) {
        CouponValidationResponse res = new CouponValidationResponse();
        res.setValid(false);
        res.setCode(code);
        res.setDiscountAmount(BigDecimal.ZERO);
        res.setOriginalSubtotal(originalSubtotal);
        res.setNewSubtotal(originalSubtotal);
        res.setMessage(message);
        return res;
    }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public BigDecimal getOriginalSubtotal() { return originalSubtotal; }
    public void setOriginalSubtotal(BigDecimal originalSubtotal) { this.originalSubtotal = originalSubtotal; }
    public BigDecimal getNewSubtotal() { return newSubtotal; }
    public void setNewSubtotal(BigDecimal newSubtotal) { this.newSubtotal = newSubtotal; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
