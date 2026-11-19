package com.novapetzlife.support;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.support.dto.AddMessageRequest;
import com.novapetzlife.support.dto.SupportTicketDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/support/tickets")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN', 'CUSTOMER_SUPPORT_VIEW')")
@Tag(name = "Admin Support Management", description = "Store owner management of customer inquiries, status updates, and staff replies")
public class AdminSupportController {

    private final SupportService supportService;

    public AdminSupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @GetMapping
    @Operation(summary = "List support tickets (Admin)", description = "Paginated tickets filtered by status (OPEN, IN_PROGRESS, RESOLVED, CLOSED)")
    public ResponseEntity<ApiResponse<Page<SupportTicketDto>>> getTickets(
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SupportTicketDto> tickets = supportService.getAllTickets(
                status,
                PageRequest.of(page, size, Sort.by("updatedAt").descending())
        );
        return ResponseEntity.ok(ApiResponse.ok(tickets));
    }

    @GetMapping("/{ticketNumber}")
    @Operation(summary = "Get support ticket detail (Admin)", description = "Retrieves full ticket history and message trail")
    public ResponseEntity<ApiResponse<SupportTicketDto>> getTicketDetail(@PathVariable String ticketNumber) {
        SupportTicketDto ticket = supportService.getTicketByNumber(ticketNumber);
        return ResponseEntity.ok(ApiResponse.ok(ticket));
    }

    @PostMapping("/{ticketNumber}/reply")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN', 'CUSTOMER_SUPPORT_EDIT')")
    @Operation(summary = "Staff reply to ticket", description = "Appends staff response and updates status to WAITING_FOR_CUSTOMER")
    public ResponseEntity<ApiResponse<SupportTicketDto>> staffReply(
            @PathVariable String ticketNumber,
            @Valid @RequestBody AddMessageRequest request) {
        request.setSenderType("STAFF");
        SupportTicketDto updated = supportService.addMessageToTicket(ticketNumber, request);
        return ResponseEntity.ok(ApiResponse.ok("Staff reply added", updated));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN', 'CUSTOMER_SUPPORT_EDIT')")
    @Operation(summary = "Update ticket status and assignment", description = "Resolves or closes customer inquiry ticket")
    public ResponseEntity<ApiResponse<SupportTicketDto>> updateTicketStatus(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assignedTo) {
        SupportTicketDto updated = supportService.updateTicketStatus(id, status, assignedTo);
        return ResponseEntity.ok(ApiResponse.ok("Ticket updated", updated));
    }
}
