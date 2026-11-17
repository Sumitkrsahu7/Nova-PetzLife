package com.novapetzlife.audit;

import com.novapetzlife.audit.dto.AuditLogDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog recordAudit(
            String entityName,
            String entityId,
            String action,
            String changedBy,
            String oldValue,
            String newValue,
            String ipAddress) {
        AuditLog auditLog = new AuditLog(
                entityName,
                entityId,
                action,
                changedBy != null ? changedBy : "ADMIN",
                oldValue,
                newValue,
                ipAddress != null ? ipAddress : "127.0.0.1"
        );
        auditLog = auditLogRepository.save(auditLog);
        log.info("[AUDIT] Action: {} on {}:{} by {}", action, entityName, entityId, auditLog.getChangedBy());
        return auditLog;
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> getAuditLogs(String entityName, String action, Pageable pageable) {
        Page<AuditLog> page;
        if (entityName != null && !entityName.isBlank() && action != null && !action.isBlank()) {
            page = auditLogRepository.findByEntityNameAndActionOrderByCreatedAtDesc(entityName, action, pageable);
        } else if (entityName != null && !entityName.isBlank()) {
            page = auditLogRepository.findByEntityNameOrderByCreatedAtDesc(entityName, pageable);
        } else if (action != null && !action.isBlank()) {
            page = auditLogRepository.findByActionOrderByCreatedAtDesc(action, pageable);
        } else {
            page = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return page.map(AuditLogDto::fromEntity);
    }
}
