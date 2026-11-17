package com.novapetzlife.order;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.novapetzlife.customer.Customer;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "order_status", nullable = false, length = 50)
    private String orderStatus; // PENDING_PAYMENT, CONFIRMED, PROCESSING, READY_FOR_DISPATCH, SHIPPED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED, RETURNED, REFUNDED

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "delivery_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal deliveryCharge = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "final_payable_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalPayableAmount;

    @Column(name = "payment_status", nullable = false, length = 50)
    private String paymentStatus = "PENDING"; // PENDING, COMPLETED, FAILED, REFUNDED

    @Column(name = "payment_method", nullable = false, length = 50)
    private String paymentMethod; // RAZORPAY, COD, OFFLINE

    @Column(name = "delivery_address_json", nullable = false, columnDefinition = "TEXT")
    private String deliveryAddressJson;

    @Column(name = "shop_distance_km", nullable = false, precision = 6, scale = 2)
    private BigDecimal shopDistanceKm;

    @Column(name = "is_within_radius", nullable = false)
    private Boolean isWithinRadius = true;

    @Column(name = "delivery_slot", length = 100)
    private String deliverySlot;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(name = "delivery_otp_hash", length = 255)
    private String deliveryOtpHash;

    @Column(name = "is_otp_verified", nullable = false)
    private Boolean isOtpVerified = false;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    @JsonManagedReference
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public Order() {}

    public Order(
            String orderNumber,
            Customer customer,
            String orderStatus,
            BigDecimal totalAmount,
            BigDecimal discountAmount,
            BigDecimal deliveryCharge,
            BigDecimal taxAmount,
            BigDecimal finalPayableAmount,
            String paymentStatus,
            String paymentMethod,
            String deliveryAddressJson,
            BigDecimal shopDistanceKm,
            Boolean isWithinRadius,
            String deliverySlot) {
        this.orderNumber = orderNumber;
        this.customer = customer;
        this.orderStatus = orderStatus;
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        this.deliveryCharge = deliveryCharge != null ? deliveryCharge : BigDecimal.ZERO;
        this.taxAmount = taxAmount != null ? taxAmount : BigDecimal.ZERO;
        this.finalPayableAmount = finalPayableAmount;
        this.paymentStatus = paymentStatus != null ? paymentStatus : "PENDING";
        this.paymentMethod = paymentMethod;
        this.deliveryAddressJson = deliveryAddressJson;
        this.shopDistanceKm = shopDistanceKm;
        this.isWithinRadius = isWithinRadius != null ? isWithinRadius : true;
        this.deliverySlot = deliverySlot;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void addStatusHistory(OrderStatusHistory history) {
        statusHistory.add(history);
        history.setOrder(this);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getDeliveryCharge() {
        return deliveryCharge;
    }

    public void setDeliveryCharge(BigDecimal deliveryCharge) {
        this.deliveryCharge = deliveryCharge;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getFinalPayableAmount() {
        return finalPayableAmount;
    }

    public void setFinalPayableAmount(BigDecimal finalPayableAmount) {
        this.finalPayableAmount = finalPayableAmount;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getDeliveryAddressJson() {
        return deliveryAddressJson;
    }

    public void setDeliveryAddressJson(String deliveryAddressJson) {
        this.deliveryAddressJson = deliveryAddressJson;
    }

    public BigDecimal getShopDistanceKm() {
        return shopDistanceKm;
    }

    public void setShopDistanceKm(BigDecimal shopDistanceKm) {
        this.shopDistanceKm = shopDistanceKm;
    }

    public Boolean getIsWithinRadius() {
        return isWithinRadius;
    }

    public void setIsWithinRadius(Boolean isWithinRadius) {
        this.isWithinRadius = isWithinRadius;
    }

    public String getDeliverySlot() {
        return deliverySlot;
    }

    public void setDeliverySlot(String deliverySlot) {
        this.deliverySlot = deliverySlot;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public List<OrderStatusHistory> getStatusHistory() {
        return statusHistory;
    }

    public void setStatusHistory(List<OrderStatusHistory> statusHistory) {
        this.statusHistory = statusHistory;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getDeliveryOtpHash() {
        return deliveryOtpHash;
    }

    public void setDeliveryOtpHash(String deliveryOtpHash) {
        this.deliveryOtpHash = deliveryOtpHash;
    }

    public Boolean getIsOtpVerified() {
        return isOtpVerified;
    }

    public void setIsOtpVerified(Boolean otpVerified) {
        this.isOtpVerified = otpVerified != null ? otpVerified : false;
    }
}
