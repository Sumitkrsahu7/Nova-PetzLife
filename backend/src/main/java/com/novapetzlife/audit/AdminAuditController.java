package com.novapetzlife.audit;

import com.novapetzlife.audit.dto.AuditLogDto;
import com.novapetzlife.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN')")
@Tag(name = "Admin Audit Logs", description = "Immutable audit logging of administrative decisions, price updates, inventory corrections, and refund approvals")
public class AdminAuditController {

    private final AuditService auditService;

    public AdminAuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "Search and filter audit logs", description = "Retrieves paginated audit trail filtered by entity type or action")
    public ResponseEntity<ApiResponse<Page<AuditLogDto>>> getAuditLogs(
            @RequestParam(required = false) String entityName,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLogDto> logs = auditService.getAuditLogs(entityName, action, pageable);
        return ResponseEntity.ok(ApiResponse.ok(logs));
    }
}
