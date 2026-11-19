package com.novapetzlife.order;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerService;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Order Processing & Tracking", description = "Order checkout, stock reservation, status tracking, and admin order fulfillment")
public class OrderController {

    private final OrderService orderService;
    private final CustomerService customerService;
    private final UserRepository userRepository;

    public OrderController(OrderService orderService, CustomerService customerService, UserRepository userRepository) {
        this.orderService = orderService;
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

    @PostMapping("/api/v1/orders/checkout")
    @Operation(summary = "Checkout shopping cart into order", description = "Validates Kuravankonam 20 km distance, reserves stock atomically, snapshots return policy, and creates order")
    public ResponseEntity<ApiResponse<OrderDetailDto>> checkout(
            Authentication authentication,
            @Valid @RequestBody CheckoutRequest request) {

        Customer customer = resolveCustomer(authentication);
        OrderDetailDto order = orderService.checkout(customer, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Order placed successfully", order));
    }

    @GetMapping("/api/v1/orders/{orderNumber}/track")
    @Operation(summary = "Track order progress", description = "Public or authenticated tracking with real-time status timeline and delivery details")
    public ResponseEntity<ApiResponse<OrderTrackingDto>> trackOrder(@PathVariable String orderNumber) {
        OrderTrackingDto tracking = orderService.getOrderTracking(orderNumber);
        return ResponseEntity.ok(ApiResponse.ok(tracking));
    }

    @GetMapping("/api/v1/customer/orders")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get customer order history", description = "Retrieves paginated orders for the authenticated customer")
    public ResponseEntity<ApiResponse<Page<OrderSummaryDto>>> getCustomerOrders(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Customer customer = resolveCustomer(authentication);
        if (customer == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Authenticated customer account required"));
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderSummaryDto> orders = orderService.getCustomerOrders(customer, pageable);
        return ResponseEntity.ok(ApiResponse.ok(orders));
    }

    @GetMapping("/api/v1/customer/orders/{orderNumber}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get customer order details", description = "Retrieves complete order detail with itemized return policy snapshots")
    public ResponseEntity<ApiResponse<OrderDetailDto>> getCustomerOrderDetail(
            Authentication authentication,
            @PathVariable String orderNumber) {

        Customer customer = resolveCustomer(authentication);
        OrderDetailDto order = orderService.getOrderDetailByNumber(orderNumber, customer);
        return ResponseEntity.ok(ApiResponse.ok(order));
    }

    // --- Admin Endpoints ---

    @GetMapping("/api/v1/admin/orders")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN', 'ORDER_VIEW')")
    @Operation(summary = "Search and filter orders (Admin)", description = "Search orders by status, order number, or address with pagination")
    public ResponseEntity<ApiResponse<Page<OrderSummaryDto>>> getAdminOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderSummaryDto> orders = orderService.adminSearchOrders(status, query, pageable);
        return ResponseEntity.ok(ApiResponse.ok(orders));
    }

    @GetMapping("/api/v1/admin/orders/{orderNumber}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN', 'ORDER_VIEW')")
    @Operation(summary = "Get order detail (Admin)", description = "Retrieve full order details, customer snapshot, items, and status history")
    public ResponseEntity<ApiResponse<OrderDetailDto>> getAdminOrderDetail(@PathVariable String orderNumber) {
        OrderDetailDto order = orderService.getOrderDetailByNumber(orderNumber, null);
        return ResponseEntity.ok(ApiResponse.ok(order));
    }

    @PatchMapping("/api/v1/admin/orders/{id}/status")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN', 'ORDER_EDIT')")
    @Operation(summary = "Update order status (Admin)", description = "Advance order lifecycle (CONFIRMED -> PACKED -> DISPATCHED -> DELIVERED, or CANCELLED with stock release)")
    public ResponseEntity<ApiResponse<OrderDetailDto>> updateOrderStatus(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody OrderStatusUpdateRequest request) {

        String operator = authentication != null ? authentication.getName() : "ADMIN_OPERATOR";
        OrderDetailDto updated = orderService.adminUpdateOrderStatus(id, request.newStatus(), operator, request.reason(), request.deliveryOtp());
        return ResponseEntity.ok(ApiResponse.ok("Order status updated successfully", updated));
    }

    @PostMapping("/api/v1/admin/orders/{id}/verify-delivery-otp")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN', 'ORDER_EDIT')")
    @Operation(summary = "Verify customer delivery OTP and complete delivery (Admin)", description = "Validates 6-digit OTP code provided by customer upon delivery and transitions order status to DELIVERED")
    public ResponseEntity<ApiResponse<OrderDetailDto>> verifyDeliveryOtp(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody VerifyDeliveryOtpRequest request) {

        String operator = authentication != null ? authentication.getName() : "ADMIN_DELIVERY_AGENT";
        OrderDetailDto updated = orderService.verifyDeliveryOtp(id, request.otp(), operator);
        return ResponseEntity.ok(ApiResponse.ok("Customer delivery OTP successfully verified. Order completed.", updated));
    }

    @PostMapping("/api/v1/admin/orders/{id}/cancel-item")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN', 'ORDER_EDIT')")
    @Operation(summary = "Cancel out-of-stock order item and process refund (Admin)", description = "Releases stock atomically, initiates pro-rated refund if order was paid online, and logs staff action")
    public ResponseEntity<ApiResponse<OrderDetailDto>> cancelOrderItem(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody CancelOrderItemRequest request) {

        String operator = authentication != null ? authentication.getName() : "STAFF_OPERATOR";
        OrderDetailDto updated = orderService.adminCancelItemOutOfStock(id, request.orderItemId(), request.reason(), operator);
        return ResponseEntity.ok(ApiResponse.ok("Order item cancelled and refund processed successfully", updated));
    }
}
