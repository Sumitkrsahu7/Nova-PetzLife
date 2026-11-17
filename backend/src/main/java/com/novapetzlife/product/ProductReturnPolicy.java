package com.novapetzlife.product;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "product_return_policies")
public class ProductReturnPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", unique = true)
    @JsonBackReference
    private Product product;

    @Column(name = "return_allowed", nullable = false)
    private Boolean returnAllowed = false;

    @Column(name = "return_window_days", nullable = false)
    private Integer returnWindowDays = 0;

    @Column(name = "original_packaging_required", nullable = false)
    private Boolean originalPackagingRequired = true;

    @Column(name = "accessories_required", nullable = false)
    private Boolean accessoriesRequired = true;

    @Column(name = "unused_condition_required", nullable = false)
    private Boolean unusedConditionRequired = true;

    @Column(name = "opened_packaging_accepted", nullable = false)
    private Boolean openedPackagingAccepted = false;

    @Column(name = "refund_allowed", nullable = false)
    private Boolean refundAllowed = false;

    @Column(name = "refund_method", length = 50)
    private String refundMethod = "ORIGINAL_PAYMENT";

    @Column(name = "physical_return_required", nullable = false)
    private Boolean physicalReturnRequired = true;

    @Column(name = "inspection_required", nullable = false)
    private Boolean inspectionRequired = true;

    @Column(name = "custom_conditions", columnDefinition = "TEXT")
    private String customConditions;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public ProductReturnPolicy() {}

    public ProductReturnPolicy(Product product, Boolean returnAllowed, Integer returnWindowDays, Boolean refundAllowed) {
        this.product = product;
        this.returnAllowed = returnAllowed != null ? returnAllowed : false;
        this.returnWindowDays = returnWindowDays != null ? returnWindowDays : 0;
        this.refundAllowed = refundAllowed != null ? refundAllowed : false;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Boolean getReturnAllowed() {
        return returnAllowed;
    }

    public void setReturnAllowed(Boolean returnAllowed) {
        this.returnAllowed = returnAllowed;
    }

    public Integer getReturnWindowDays() {
        return returnWindowDays;
    }

    public void setReturnWindowDays(Integer returnWindowDays) {
        this.returnWindowDays = returnWindowDays;
    }

    public Boolean getOriginalPackagingRequired() {
        return originalPackagingRequired;
    }

    public void setOriginalPackagingRequired(Boolean originalPackagingRequired) {
        this.originalPackagingRequired = originalPackagingRequired;
    }

    public Boolean getAccessoriesRequired() {
        return accessoriesRequired;
    }

    public void setAccessoriesRequired(Boolean accessoriesRequired) {
        this.accessoriesRequired = accessoriesRequired;
    }

    public Boolean getUnusedConditionRequired() {
        return unusedConditionRequired;
    }

    public void setUnusedConditionRequired(Boolean unusedConditionRequired) {
        this.unusedConditionRequired = unusedConditionRequired;
    }

    public Boolean getOpenedPackagingAccepted() {
        return openedPackagingAccepted;
    }

    public void setOpenedPackagingAccepted(Boolean openedPackagingAccepted) {
        this.openedPackagingAccepted = openedPackagingAccepted;
    }

    public Boolean getRefundAllowed() {
        return refundAllowed;
    }

    public void setRefundAllowed(Boolean refundAllowed) {
        this.refundAllowed = refundAllowed;
    }

    public String getRefundMethod() {
        return refundMethod;
    }

    public void setRefundMethod(String refundMethod) {
        this.refundMethod = refundMethod;
    }

    public Boolean getPhysicalReturnRequired() {
        return physicalReturnRequired;
    }

    public void setPhysicalReturnRequired(Boolean physicalReturnRequired) {
        this.physicalReturnRequired = physicalReturnRequired;
    }

    public Boolean getInspectionRequired() {
        return inspectionRequired;
    }

    public void setInspectionRequired(Boolean inspectionRequired) {
        this.inspectionRequired = inspectionRequired;
    }

    public String getCustomConditions() {
        return customConditions;
    }

    public void setCustomConditions(String customConditions) {
        this.customConditions = customConditions;
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
