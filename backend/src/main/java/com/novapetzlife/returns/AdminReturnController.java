package com.novapetzlife.returns;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.returns.dto.InspectReturnRequest;
import com.novapetzlife.returns.dto.ReturnRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/returns")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN')")
@Tag(name = "Admin Returns Management", description = "Shop administration workflows for scheduling pickups, inspecting returned items, restock handling, and approving refunds")
public class AdminReturnController {

    private final ReturnService returnService;

    public AdminReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    @GetMapping
    @Operation(summary = "List return requests", description = "Paginated list of customer return requests filtered by status")
    public ResponseEntity<ApiResponse<Page<ReturnRequestDto>>> getReturnRequests(
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        Page<ReturnRequestDto> results = returnService.getReturnRequests(
                status,
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    @GetMapping("/{requestNumber}")
    @Operation(summary = "Get return request details", description = "Full details of a return request with item breakdown and inspection logs")
    public ResponseEntity<ApiResponse<ReturnRequestDto>> getReturnRequest(@PathVariable String requestNumber) {
        ReturnRequestDto result = returnService.getReturnRequestByNumber(requestNumber);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/{id}/pickup")
    @Operation(summary = "Schedule return pickup", description = "Sets status to PICKUP_SCHEDULED for store runner within Trivandrum delivery radius")
    public ResponseEntity<ApiResponse<ReturnRequestDto>> schedulePickup(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Principal principal) {
        String notes = body != null ? body.get("adminNotes") : null;
        String staff = principal != null ? principal.getName() : "ADMIN_STAFF";
        ReturnRequestDto result = returnService.adminSchedulePickup(id, notes, staff);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/{id}/receive")
    @Operation(summary = "Mark returned item received at store", description = "Confirms package received back at store location")
    public ResponseEntity<ApiResponse<ReturnRequestDto>> receiveReturn(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Principal principal) {
        String notes = body != null ? body.get("adminNotes") : null;
        String staff = principal != null ? principal.getName() : "ADMIN_STAFF";
        ReturnRequestDto result = returnService.adminReceiveReturn(id, notes, staff);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/{id}/inspect")
    @Operation(summary = "Submit physical inspection & refund decision", description = "Records physical inspection outcome, optionally restocks undamaged items, and processes customer refund")
    public ResponseEntity<ApiResponse<ReturnRequestDto>> inspectReturn(
            @PathVariable Long id,
            @Valid @RequestBody InspectReturnRequest request,
            Principal principal) {
        String staff = principal != null ? principal.getName() : "ADMIN_STAFF";
        ReturnRequestDto result = returnService.adminInspectReturn(id, request, staff);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
