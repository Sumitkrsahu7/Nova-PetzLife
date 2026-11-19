package com.novapetzlife.returns.dto;

import com.novapetzlife.returns.ReturnRequest;
import com.novapetzlife.returns.ReturnRequestItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class ReturnRequestDto {

    private Long id;
    private String requestNumber;
    private String orderNumber;
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String status;
    private String reason;
    private String customerComments;
    private String adminNotes;
    private BigDecimal estimatedRefundAmount;
    private List<ReturnItemDto> items;
    private List<InspectionDto> inspections;
    private Instant createdAt;
    private Instant updatedAt;

    public ReturnRequestDto() {}

    public static class ReturnItemDto {
        private Long id;
        private Long orderItemId;
        private String productName;
        private String brandName;
        private String sku;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;

        public ReturnItemDto() {}

        public ReturnItemDto(Long id, Long orderItemId, String productName, String brandName, String sku, Integer quantity, BigDecimal unitPrice, BigDecimal totalPrice) {
            this.id = id;
            this.orderItemId = orderItemId;
            this.productName = productName;
            this.brandName = brandName;
            this.sku = sku;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.totalPrice = totalPrice;
        }

        public Long getId() { return id; }
        public Long getOrderItemId() { return orderItemId; }
        public String getProductName() { return productName; }
        public String getBrandName() { return brandName; }
        public String getSku() { return sku; }
        public Integer getQuantity() { return quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public BigDecimal getTotalPrice() { return totalPrice; }
    }

    public static class InspectionDto {
        private Long id;
        private String inspectedBy;
        private String outcome;
        private String conditionNotes;
        private Instant inspectedAt;

        public InspectionDto() {}

        public InspectionDto(Long id, String inspectedBy, String outcome, String conditionNotes, Instant inspectedAt) {
            this.id = id;
            this.inspectedBy = inspectedBy;
            this.outcome = outcome;
            this.conditionNotes = conditionNotes;
            this.inspectedAt = inspectedAt;
        }

        public Long getId() { return id; }
        public String getInspectedBy() { return inspectedBy; }
        public String getOutcome() { return outcome; }
        public String getConditionNotes() { return conditionNotes; }
        public Instant getInspectedAt() { return inspectedAt; }
    }

    public static ReturnRequestDto fromEntity(ReturnRequest req) {
        ReturnRequestDto dto = new ReturnRequestDto();
        dto.setId(req.getId());
        dto.setRequestNumber(req.getRequestNumber());
        dto.setOrderNumber(req.getOrder().getOrderNumber());
        dto.setStatus(req.getStatus());
        dto.setReason(req.getReason());
        dto.setCustomerComments(req.getCustomerComments());
        dto.setAdminNotes(req.getAdminNotes());
        dto.setCreatedAt(req.getCreatedAt());
        dto.setUpdatedAt(req.getUpdatedAt());

        if (req.getCustomer() != null) {
            dto.setCustomerName((req.getCustomer().getFirstName() + " " + (req.getCustomer().getLastName() != null ? req.getCustomer().getLastName() : "")).trim());
            dto.setCustomerPhone(req.getCustomer().getPhone());
            dto.setCustomerEmail(req.getCustomer().getEmail());
        }

        BigDecimal sum = BigDecimal.ZERO;
        if (req.getItems() != null) {
            dto.setItems(req.getItems().stream().map(it -> {
                BigDecimal linePrice = it.getOrderItem().getUnitPrice().multiply(BigDecimal.valueOf(it.getQuantity()));
                return new ReturnItemDto(
                        it.getId(),
                        it.getOrderItem().getId(),
                        it.getOrderItem().getProductName(),
                        it.getOrderItem().getBrandName(),
                        it.getOrderItem().getSku(),
                        it.getQuantity(),
                        it.getOrderItem().getUnitPrice(),
                        linePrice
                );
            }).toList());

            for (ReturnRequestItem it : req.getItems()) {
                sum = sum.add(it.getOrderItem().getUnitPrice().multiply(BigDecimal.valueOf(it.getQuantity())));
            }
        }
        dto.setEstimatedRefundAmount(sum);

        if (req.getInspections() != null) {
            dto.setInspections(req.getInspections().stream().map(ins ->
                    new InspectionDto(ins.getId(), ins.getInspectedBy(), ins.getOutcome(), ins.getConditionNotes(), ins.getInspectedAt())
            ).toList());
        }

        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequestNumber() { return requestNumber; }
    public void setRequestNumber(String requestNumber) { this.requestNumber = requestNumber; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getCustomerComments() { return customerComments; }
    public void setCustomerComments(String customerComments) { this.customerComments = customerComments; }
    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }
    public BigDecimal getEstimatedRefundAmount() { return estimatedRefundAmount; }
    public void setEstimatedRefundAmount(BigDecimal estimatedRefundAmount) { this.estimatedRefundAmount = estimatedRefundAmount; }
    public List<ReturnItemDto> getItems() { return items; }
    public void setItems(List<ReturnItemDto> items) { this.items = items; }
    public List<InspectionDto> getInspections() { return inspections; }
    public void setInspections(List<InspectionDto> inspections) { this.inspections = inspections; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
