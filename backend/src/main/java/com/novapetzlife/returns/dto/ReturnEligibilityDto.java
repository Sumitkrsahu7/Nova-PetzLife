package com.novapetzlife.returns.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ReturnEligibilityDto {

    private String orderNumber;
    private String orderStatus;
    private String orderDate;
    private boolean canRequestReturn;
    private String generalEligibilityMessage;
    private List<ItemEligibilityDto> items;

    public ReturnEligibilityDto() {}

    public ReturnEligibilityDto(
            String orderNumber,
            String orderStatus,
            String orderDate,
            boolean canRequestReturn,
            String generalEligibilityMessage,
            List<ItemEligibilityDto> items) {
        this.orderNumber = orderNumber;
        this.orderStatus = orderStatus;
        this.orderDate = orderDate;
        this.canRequestReturn = canRequestReturn;
        this.generalEligibilityMessage = generalEligibilityMessage;
        this.items = items;
    }

    public static class ItemEligibilityDto {
        private Long orderItemId;
        private Long productId;
        private String productName;
        private String brandName;
        private String sku;
        private Integer quantity;
        private BigDecimal unitPrice;
        private boolean isReturnAllowed;
        private int returnWindowDays;
        private LocalDate returnDeadline;
        private boolean isWithinWindow;
        private boolean isEligible;
        private String reasonIfNotEligible;
        private boolean originalPackagingRequired;
        private boolean unusedConditionRequired;
        private String conditions;

        public ItemEligibilityDto() {}

        public ItemEligibilityDto(
                Long orderItemId,
                Long productId,
                String productName,
                String brandName,
                String sku,
                Integer quantity,
                BigDecimal unitPrice,
                boolean isReturnAllowed,
                int returnWindowDays,
                LocalDate returnDeadline,
                boolean isWithinWindow,
                boolean isEligible,
                String reasonIfNotEligible,
                boolean originalPackagingRequired,
                boolean unusedConditionRequired,
                String conditions) {
            this.orderItemId = orderItemId;
            this.productId = productId;
            this.productName = productName;
            this.brandName = brandName;
            this.sku = sku;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.isReturnAllowed = isReturnAllowed;
            this.returnWindowDays = returnWindowDays;
            this.returnDeadline = returnDeadline;
            this.isWithinWindow = isWithinWindow;
            this.isEligible = isEligible;
            this.reasonIfNotEligible = reasonIfNotEligible;
            this.originalPackagingRequired = originalPackagingRequired;
            this.unusedConditionRequired = unusedConditionRequired;
            this.conditions = conditions;
        }

        public Long getOrderItemId() { return orderItemId; }
        public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getBrandName() { return brandName; }
        public void setBrandName(String brandName) { this.brandName = brandName; }
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public boolean isReturnAllowed() { return isReturnAllowed; }
        public void setReturnAllowed(boolean returnAllowed) { isReturnAllowed = returnAllowed; }
        public int getReturnWindowDays() { return returnWindowDays; }
        public void setReturnWindowDays(int returnWindowDays) { this.returnWindowDays = returnWindowDays; }
        public LocalDate getReturnDeadline() { return returnDeadline; }
        public void setReturnDeadline(LocalDate returnDeadline) { this.returnDeadline = returnDeadline; }
        public boolean isWithinWindow() { return isWithinWindow; }
        public void setWithinWindow(boolean withinWindow) { isWithinWindow = withinWindow; }
        public boolean isEligible() { return isEligible; }
        public void setEligible(boolean eligible) { isEligible = eligible; }
        public String getReasonIfNotEligible() { return reasonIfNotEligible; }
        public void setReasonIfNotEligible(String reasonIfNotEligible) { this.reasonIfNotEligible = reasonIfNotEligible; }
        public boolean isOriginalPackagingRequired() { return originalPackagingRequired; }
        public void setOriginalPackagingRequired(boolean originalPackagingRequired) { this.originalPackagingRequired = originalPackagingRequired; }
        public boolean isUnusedConditionRequired() { return unusedConditionRequired; }
        public void setUnusedConditionRequired(boolean unusedConditionRequired) { this.unusedConditionRequired = unusedConditionRequired; }
        public String getConditions() { return conditions; }
        public void setConditions(String conditions) { this.conditions = conditions; }
    }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }
    public String getOrderDate() { return orderDate; }
    public void setOrderDate(String orderDate) { this.orderDate = orderDate; }
    public boolean isCanRequestReturn() { return canRequestReturn; }
    public void setCanRequestReturn(boolean canRequestReturn) { this.canRequestReturn = canRequestReturn; }
    public String getGeneralEligibilityMessage() { return generalEligibilityMessage; }
    public void setGeneralEligibilityMessage(String generalEligibilityMessage) { this.generalEligibilityMessage = generalEligibilityMessage; }
    public List<ItemEligibilityDto> getItems() { return items; }
    public void setItems(List<ItemEligibilityDto> items) { this.items = items; }
}
