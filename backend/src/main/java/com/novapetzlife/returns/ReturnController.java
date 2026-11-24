package com.novapetzlife.returns;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerService;
import com.novapetzlife.order.OrderDetailDto;
import com.novapetzlife.returns.dto.CancelOrderRequest;
import com.novapetzlife.returns.dto.CreateReturnRequest;
import com.novapetzlife.returns.dto.ReturnEligibilityDto;
import com.novapetzlife.returns.dto.ReturnRequestDto;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Returns & Cancellations", description = "Customer return eligibility, return request submission, and pre-dispatch order cancellations")
public class ReturnController {

    private final ReturnService returnService;
    private final CustomerService customerService;
    private final UserRepository userRepository;

    public ReturnController(
            ReturnService returnService,
            CustomerService customerService,
            UserRepository userRepository) {
        this.returnService = returnService;
        this.customerService = customerService;
        this.userRepository = userRepository;
    }

    private Customer resolveCustomer(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            User user = userRepository.findByEmail(authentication.getName().toLowerCase().trim()).orElse(null);
            if (user != null) {
                return customerService.getOrCreateCustomerForUser(user);
            }
        }
        return null;
    }

    private boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()) || "ROLE_STAFF_ADMIN".equals(a.getAuthority()));
    }

    @GetMapping("/orders/{orderNumber}/return-eligibility")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Check order return eligibility", description = "Compares delivery date with immutable item-level return policy snapshots (window days, packaging conditions)")
    public ResponseEntity<ApiResponse<ReturnEligibilityDto>> checkReturnEligibility(
            @PathVariable String orderNumber,
            Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            throw new AccessDeniedException("Authentication required to check return eligibility");
        }
        Customer customer = resolveCustomer(auth);
        boolean admin = isAdmin(auth);
        ReturnEligibilityDto eligibility = returnService.checkOrderReturnEligibility(orderNumber, customer, admin);
        return ResponseEntity.ok(ApiResponse.ok(eligibility));
    }

    @PostMapping("/returns")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Submit return request", description = "Submits customer return request for delivered order items")
    public ResponseEntity<ApiResponse<ReturnRequestDto>> submitReturnRequest(
            @Valid @RequestBody CreateReturnRequest request,
            Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            throw new AccessDeniedException("Authentication required to submit a return request");
        }
        Customer customer = resolveCustomer(auth);
        if (customer == null && !isAdmin(auth)) {
            throw new AccessDeniedException("Customer profile required to submit a return request");
        }
        ReturnRequestDto result = returnService.createReturnRequest(customer, request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/returns/{requestNumber}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Track return request status", description = "Retrieves live status, inspection notes, and refund details for a return request")
    public ResponseEntity<ApiResponse<ReturnRequestDto>> getReturnRequest(
            @PathVariable String requestNumber,
            Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            throw new AccessDeniedException("Authentication required to view return details");
        }
        Customer customer = resolveCustomer(auth);
        boolean admin = isAdmin(auth);
        ReturnRequestDto result = returnService.getReturnRequestByNumber(requestNumber, customer, admin);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/orders/{orderNumber}/cancel")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cancel order prior to delivery", description = "Cancels order, releases reserved inventory stock, and auto-initiates refund if paid online")
    public ResponseEntity<ApiResponse<OrderDetailDto>> cancelOrder(
            @PathVariable String orderNumber,
            @Valid @RequestBody CancelOrderRequest request,
            Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            throw new AccessDeniedException("Authentication required to cancel an order");
        }
        Customer customer = resolveCustomer(auth);
        boolean admin = isAdmin(auth);
        String operator = auth.getName();
        OrderDetailDto result = returnService.cancelOrder(orderNumber, request.getReason(), operator, customer, admin);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
