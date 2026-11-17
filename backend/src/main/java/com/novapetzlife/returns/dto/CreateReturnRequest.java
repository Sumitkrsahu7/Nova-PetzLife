package com.novapetzlife.returns.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class CreateReturnRequest {

    @NotBlank(message = "Order number is required")
    private String orderNumber;

    @NotEmpty(message = "At least one item must be selected for return")
    private List<ReturnItemRequest> items;

    @NotBlank(message = "Return reason is required")
    private String reason;

    private String customerComments;

    public CreateReturnRequest() {}

    public CreateReturnRequest(String orderNumber, List<ReturnItemRequest> items, String reason, String customerComments) {
        this.orderNumber = orderNumber;
        this.items = items;
        this.reason = reason;
        this.customerComments = customerComments;
    }

    public static class ReturnItemRequest {
        @NotNull(message = "Order item ID is required")
        private Long orderItemId;

        @NotNull(message = "Quantity is required")
        private Integer quantity;

        public ReturnItemRequest() {}

        public ReturnItemRequest(Long orderItemId, Integer quantity) {
            this.orderItemId = orderItemId;
            this.quantity = quantity;
        }

        public Long getOrderItemId() { return orderItemId; }
        public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public List<ReturnItemRequest> getItems() { return items; }
    public void setItems(List<ReturnItemRequest> items) { this.items = items; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getCustomerComments() { return customerComments; }
    public void setCustomerComments(String customerComments) { this.customerComments = customerComments; }
}
