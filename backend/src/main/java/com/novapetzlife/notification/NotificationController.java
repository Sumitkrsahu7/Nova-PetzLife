package com.novapetzlife.notification;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerRepository;
import com.novapetzlife.customer.CustomerService;
import com.novapetzlife.notification.dto.NotificationDto;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customer/notifications")
@Tag(name = "Customer Notifications", description = "Customer inbox of order confirmations, dispatch updates, and delivery alerts")
public class NotificationController {

    private final NotificationService notificationService;
    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    public NotificationController(
            NotificationService notificationService,
            CustomerService customerService,
            CustomerRepository customerRepository,
            UserRepository userRepository) {
        this.notificationService = notificationService;
        this.customerService = customerService;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
    }

    private Customer resolveCustomer(Authentication authentication, Long customerId) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new AccessDeniedException("Full authentication is required to access customer notifications");
        }

        boolean isAdmin = authentication.getAuthorities() != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()) || "ROLE_STAFF_ADMIN".equals(a.getAuthority()));

        // Authorized store staff/super admins may optionally view notifications for a specific customer
        if (isAdmin && customerId != null) {
            return customerRepository.findById(customerId).orElse(null);
        }

        // Standard authenticated customers strictly resolve their own customer record from the authenticated user.
        // Caller-supplied customerId is ignored for standard customers to prevent Customer A accessing Customer B's notifications.
        User user = userRepository.findByEmail(authentication.getName().toLowerCase().trim()).orElse(null);
        if (user == null) {
            return null;
        }

        return customerService.getOrCreateCustomerForUser(user);
    }

    @GetMapping
    @Operation(summary = "Get customer notifications", description = "Retrieves recent notifications sent to the customer")
    public ResponseEntity<ApiResponse<List<NotificationDto>>> getCustomerNotifications(
            Authentication authentication,
            @RequestParam(required = false) Long customerId) {
        Customer customer = resolveCustomer(authentication, customerId);
        if (customer == null) {
            return ResponseEntity.ok(ApiResponse.ok(List.of()));
        }
        List<NotificationDto> notifications = notificationService.getRecentCustomerNotifications(customer)
                .stream()
                .map(NotificationDto::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(notifications));
    }
}
