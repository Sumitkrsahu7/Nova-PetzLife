package com.novapetzlife.delivery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record DeliverySlotCreateRequest(
        @NotBlank(message = "Slot name is required")
        String slotName,

        @NotNull(message = "Start time is required")
        LocalTime startTime,

        @NotNull(message = "End time is required")
        LocalTime endTime,

        Integer maxCapacity,
        Boolean isActive,
        String availableDays
) {}
