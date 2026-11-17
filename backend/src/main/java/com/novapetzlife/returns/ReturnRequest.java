package com.novapetzlife.returns;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.order.Order;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "return_requests")
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_number", nullable = false, unique = true, length = 50)
    private String requestNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonBackReference
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, length = 50)
    private String status = "REQUESTED"; // REQUESTED, APPROVED, REJECTED, PICKUP_SCHEDULED, RECEIVED, INSPECTED, REFUND_INITIATED, CLOSED

    @Column(nullable = false, length = 100)
    private String reason;

    @Column(name = "customer_comments", columnDefinition = "TEXT")
    private String customerComments;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<ReturnRequestItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("inspectedAt DESC")
    @JsonManagedReference
    private List<ReturnInspection> inspections = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public ReturnRequest() {}

    public ReturnRequest(String requestNumber, Order order, Customer customer, String reason, String customerComments) {
        this.requestNumber = requestNumber;
        this.order = order;
        this.customer = customer;
        this.status = "REQUESTED";
        this.reason = reason;
        this.customerComments = customerComments;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void addItem(ReturnRequestItem item) {
        items.add(item);
        item.setReturnRequest(this);
    }

    public void addInspection(ReturnInspection inspection) {
        inspections.add(inspection);
        inspection.setReturnRequest(this);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequestNumber() {
        return requestNumber;
    }

    public void setRequestNumber(String requestNumber) {
        this.requestNumber = requestNumber;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
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

    public String getCustomerComments() {
        return customerComments;
    }

    public void setCustomerComments(String customerComments) {
        this.customerComments = customerComments;
    }

    public String getAdminNotes() {
        return adminNotes;
    }

    public void setAdminNotes(String adminNotes) {
        this.adminNotes = adminNotes;
    }

    public List<ReturnRequestItem> getItems() {
        return items;
    }

    public java.math.BigDecimal getEstimatedRefundAmount() {
        if (items == null || items.isEmpty()) return java.math.BigDecimal.ZERO;
        return items.stream()
                .map(it -> it.getOrderItem() != null && it.getOrderItem().getUnitPrice() != null
                        ? it.getOrderItem().getUnitPrice().multiply(java.math.BigDecimal.valueOf(it.getQuantity()))
                        : java.math.BigDecimal.ZERO)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }

    public void setItems(List<ReturnRequestItem> items) {
        this.items = items;
    }

    public List<ReturnInspection> getInspections() {
        return inspections;
    }

    public void setInspections(List<ReturnInspection> inspections) {
        this.inspections = inspections;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
