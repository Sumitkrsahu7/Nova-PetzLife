package com.novapetzlife.notification;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.notification.dto.NotificationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN')")
@Tag(name = "Admin Notifications", description = "Audit trail of outgoing SMS, Email, and WhatsApp customer notifications")
public class AdminNotificationController {

    private final NotificationService notificationService;

    public AdminNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "List all dispatched notifications", description = "Paginated list of all system notifications sent to customers and shop owner")
    public ResponseEntity<ApiResponse<Page<NotificationDto>>> getAllNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<NotificationDto> notifications = notificationService.getAllNotifications(
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        ).map(NotificationDto::fromEntity);
        return ResponseEntity.ok(ApiResponse.ok(notifications));
    }
}
