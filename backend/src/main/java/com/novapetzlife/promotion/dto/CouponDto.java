package com.novapetzlife.promotion.dto;

import com.novapetzlife.promotion.Coupon;
import java.math.BigDecimal;
import java.time.Instant;

public class CouponDto {

    private Long id;
    private String code;
    private String description;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal minOrderValue;
    private BigDecimal maxDiscountAmount;
    private Integer usageLimit;
    private Integer usageCount;
    private Instant startDate;
    private Instant endDate;
    private Boolean isActive;
    private boolean isCurrentlyValid;

    public CouponDto() {}

    public static CouponDto fromEntity(Coupon c) {
        CouponDto dto = new CouponDto();
        dto.setId(c.getId());
        dto.setCode(c.getCode());
        dto.setDescription(c.getDescription());
        dto.setDiscountType(c.getDiscountType());
        dto.setDiscountValue(c.getDiscountValue());
        dto.setMinOrderValue(c.getMinOrderValue());
        dto.setMaxDiscountAmount(c.getMaxDiscountAmount());
        dto.setUsageLimit(c.getUsageLimit());
        dto.setUsageCount(c.getUsageCount());
        dto.setStartDate(c.getStartDate());
        dto.setEndDate(c.getEndDate());
        dto.setIsActive(c.getIsActive());
        dto.setCurrentlyValid(c.isCurrentlyValid());
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public Integer getUsageCount() { return usageCount; }
    public void setUsageCount(Integer usageCount) { this.usageCount = usageCount; }
    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }
    public Instant getEndDate() { return endDate; }
    public void setEndDate(Instant endDate) { this.endDate = endDate; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }
    public boolean isCurrentlyValid() { return isCurrentlyValid; }
    public void setCurrentlyValid(boolean currentlyValid) { isCurrentlyValid = currentlyValid; }
}
