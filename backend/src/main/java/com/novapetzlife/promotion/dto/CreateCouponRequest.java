package com.novapetzlife.promotion.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public class CreateCouponRequest {

    @NotBlank(message = "Coupon code is required")
    private String code;

    private String description;

    @NotBlank(message = "Discount type is required (PERCENTAGE, FIXED)")
    private String discountType;

    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "0.01", message = "Discount value must be greater than zero")
    private BigDecimal discountValue;

    private BigDecimal minOrderValue = BigDecimal.ZERO;
    private BigDecimal maxDiscountAmount;
    private Integer usageLimit;
    private Instant startDate;
    private Instant endDate;
    private Boolean isActive = true;

    public CreateCouponRequest() {}

    public CreateCouponRequest(String code, String description, String discountType, BigDecimal discountValue, BigDecimal minOrderValue, BigDecimal maxDiscountAmount, Integer usageLimit, Instant startDate, Instant endDate, Boolean isActive) {
        this.code = code;
        this.description = description;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderValue = minOrderValue;
        this.maxDiscountAmount = maxDiscountAmount;
        this.usageLimit = usageLimit;
        this.startDate = startDate;
        this.endDate = endDate;
        this.isActive = isActive;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }
    public BigDecimal getMinOrderValue() { return minOrderValue; }
    public void setMinOrderValue(BigDecimal minOrderValue) { this.minOrderValue = minOrderValue; }
    public BigDecimal getMaxDiscountAmount() { return maxDiscountAmount; }
    public void setMaxDiscountAmount(BigDecimal maxDiscountAmount) { this.maxDiscountAmount = maxDiscountAmount; }
    public Integer getUsageLimit() { return usageLimit; }
    public void setUsageLimit(Integer usageLimit) { this.usageLimit = usageLimit; }
    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }
    public Instant getEndDate() { return endDate; }
    public void setEndDate(Instant endDate) { this.endDate = endDate; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }
}
