package com.novapetzlife.refund;

import java.math.BigDecimal;
import java.time.Instant;

public record RefundSummaryDto(
        Long id,
        String gatewayRefundId,
        BigDecimal amount,
        String status,
        String reason,
        String approvedBy,
        Instant initiatedAt,
        Instant completedAt
) {
    public static RefundSummaryDto fromEntity(Refund r) {
        if (r == null) return null;
        return new RefundSummaryDto(
                r.getId(),
                r.getGatewayRefundId(),
                r.getAmount(),
                r.getStatus(),
                r.getReason(),
                r.getApprovedBy(),
                r.getInitiatedAt(),
                r.getCompletedAt()
        );
    }
}
