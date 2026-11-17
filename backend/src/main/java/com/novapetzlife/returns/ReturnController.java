package com.novapetzlife.returns;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.order.OrderDetailDto;
import com.novapetzlife.returns.dto.CancelOrderRequest;
import com.novapetzlife.returns.dto.CreateReturnRequest;
import com.novapetzlife.returns.dto.ReturnEligibilityDto;
import com.novapetzlife.returns.dto.ReturnRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Returns & Cancellations", description = "Customer return eligibility, return request submission, and pre-dispatch order cancellations")
public class ReturnController {

    private final ReturnService returnService;

    public ReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    @GetMapping("/orders/{orderNumber}/return-eligibility")
    @Operation(summary = "Check order return eligibility", description = "Compares delivery date with immutable item-level return policy snapshots (window days, packaging conditions)")
    public ResponseEntity<ApiResponse<ReturnEligibilityDto>> checkReturnEligibility(@PathVariable String orderNumber) {
        ReturnEligibilityDto eligibility = returnService.checkOrderReturnEligibility(orderNumber, null);
        return ResponseEntity.ok(ApiResponse.ok(eligibility));
    }

    @PostMapping("/returns")
    @Operation(summary = "Submit return request", description = "Submits customer return request for delivered order items")
    public ResponseEntity<ApiResponse<ReturnRequestDto>> submitReturnRequest(@Valid @RequestBody CreateReturnRequest request) {
        ReturnRequestDto result = returnService.createReturnRequest(null, request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/returns/{requestNumber}")
    @Operation(summary = "Track return request status", description = "Retrieves live status, inspection notes, and refund details for a return request")
    public ResponseEntity<ApiResponse<ReturnRequestDto>> getReturnRequest(@PathVariable String requestNumber) {
        ReturnRequestDto result = returnService.getReturnRequestByNumber(requestNumber);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/orders/{orderNumber}/cancel")
    @Operation(summary = "Cancel order prior to delivery", description = "Cancels order, releases reserved inventory stock, and auto-initiates refund if paid online")
    public ResponseEntity<ApiResponse<OrderDetailDto>> cancelOrder(
            @PathVariable String orderNumber,
            @Valid @RequestBody CancelOrderRequest request) {
        OrderDetailDto result = returnService.cancelOrder(orderNumber, request.getReason(), "CUSTOMER", true);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
