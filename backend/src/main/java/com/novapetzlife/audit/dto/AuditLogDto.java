package com.novapetzlife.audit.dto;

import com.novapetzlife.audit.AuditLog;
import java.time.Instant;

public record AuditLogDto(
        Long id,
        String entityName,
        String entityId,
        String action,
        String changedBy,
        String oldValue,
        String newValue,
        String ipAddress,
        Instant createdAt
) {
    public static AuditLogDto fromEntity(AuditLog log) {
        return new AuditLogDto(
                log.getId(),
                log.getEntityName(),
                log.getEntityId(),
                log.getAction(),
                log.getChangedBy(),
                log.getOldValue(),
                log.getNewValue(),
                log.getIpAddress(),
                log.getCreatedAt()
        );
    }
}
