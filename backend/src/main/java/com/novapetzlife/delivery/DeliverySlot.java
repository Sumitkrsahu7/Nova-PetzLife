package com.novapetzlife.delivery;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "delivery_slots")
public class DeliverySlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slot_name", nullable = false, length = 100)
    private String slotName;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "max_capacity", nullable = false)
    private Integer maxCapacity = 20;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "available_days", nullable = false, length = 100)
    private String availableDays = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY";

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    public DeliverySlot() {}

    public DeliverySlot(String slotName, LocalTime startTime, LocalTime endTime, Integer maxCapacity, Boolean isActive) {
        this(slotName, startTime, endTime, maxCapacity, isActive, "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY");
    }

    public DeliverySlot(String slotName, LocalTime startTime, LocalTime endTime, Integer maxCapacity, Boolean isActive, String availableDays) {
        this.slotName = slotName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.maxCapacity = maxCapacity != null ? maxCapacity : 20;
        this.isActive = isActive != null ? isActive : true;
        this.availableDays = availableDays != null && !availableDays.isBlank()
                ? availableDays
                : "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY";
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSlotName() {
        return slotName;
    }

    public void setSlotName(String slotName) {
        this.slotName = slotName;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public Integer getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(Integer maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public String getAvailableDays() {
        return availableDays;
    }

    public void setAvailableDays(String availableDays) {
        this.availableDays = availableDays;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
