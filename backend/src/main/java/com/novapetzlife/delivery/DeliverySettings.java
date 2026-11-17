package com.novapetzlife.delivery;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "delivery_settings")
public class DeliverySettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal deliveryCharge = new BigDecimal("50.00");

    @Column(name = "free_delivery_threshold", precision = 12, scale = 2)
    private BigDecimal freeDeliveryThreshold = new BigDecimal("500.00");

    @Column(name = "min_order_value", precision = 12, scale = 2)
    private BigDecimal minOrderValue = new BigDecimal("100.00");

    @Column(name = "prep_time_minutes", nullable = false)
    private Integer prepTimeMinutes = 60;

    @Column(name = "is_delivery_enabled", nullable = false)
    private Boolean isDeliveryEnabled = true;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public DeliverySettings() {}

    public DeliverySettings(
            BigDecimal deliveryCharge,
            BigDecimal freeDeliveryThreshold,
            BigDecimal minOrderValue,
            Integer prepTimeMinutes,
            Boolean isDeliveryEnabled) {
        this.deliveryCharge = deliveryCharge != null ? deliveryCharge : new BigDecimal("50.00");
        this.freeDeliveryThreshold = freeDeliveryThreshold != null ? freeDeliveryThreshold : new BigDecimal("500.00");
        this.minOrderValue = minOrderValue != null ? minOrderValue : new BigDecimal("100.00");
        this.prepTimeMinutes = prepTimeMinutes != null ? prepTimeMinutes : 60;
        this.isDeliveryEnabled = isDeliveryEnabled != null ? isDeliveryEnabled : true;
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

    public BigDecimal getDeliveryCharge() {
        return deliveryCharge;
    }

    public void setDeliveryCharge(BigDecimal deliveryCharge) {
        this.deliveryCharge = deliveryCharge;
    }

    public BigDecimal getFreeDeliveryThreshold() {
        return freeDeliveryThreshold;
    }

    public void setFreeDeliveryThreshold(BigDecimal freeDeliveryThreshold) {
        this.freeDeliveryThreshold = freeDeliveryThreshold;
    }

    public BigDecimal getMinOrderValue() {
        return minOrderValue;
    }

    public void setMinOrderValue(BigDecimal minOrderValue) {
        this.minOrderValue = minOrderValue;
    }

    public Integer getPrepTimeMinutes() {
        return prepTimeMinutes;
    }

    public void setPrepTimeMinutes(Integer prepTimeMinutes) {
        this.prepTimeMinutes = prepTimeMinutes;
    }

    public Boolean getIsDeliveryEnabled() {
        return isDeliveryEnabled;
    }

    public void setIsDeliveryEnabled(Boolean isDeliveryEnabled) {
        this.isDeliveryEnabled = isDeliveryEnabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
