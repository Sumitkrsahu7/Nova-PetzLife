package com.novapetzlife.order;

import java.time.Instant;

public record OrderStatusHistoryDto(
        Long id,
        String previousStatus,
        String newStatus,
        String changedBy,
        String reason,
        Instant createdAt
) {
    public static OrderStatusHistoryDto fromEntity(OrderStatusHistory h) {
        return new OrderStatusHistoryDto(
                h.getId(),
                h.getPreviousStatus(),
                h.getNewStatus(),
                h.getChangedBy(),
                h.getReason(),
                h.getCreatedAt()
        );
    }
}
