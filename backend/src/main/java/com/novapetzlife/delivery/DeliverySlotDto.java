package com.novapetzlife.delivery;

import java.time.LocalTime;

public record DeliverySlotDto(
        Long id,
        String slotName,
        LocalTime startTime,
        LocalTime endTime,
        Integer maxCapacity,
        Boolean isActive,
        String availableDays
) {
    public static DeliverySlotDto fromEntity(DeliverySlot s) {
        return new DeliverySlotDto(
                s.getId(),
                s.getSlotName(),
                s.getStartTime(),
                s.getEndTime(),
                s.getMaxCapacity(),
                s.getIsActive(),
                s.getAvailableDays()
        );
    }
}
