package com.novapetzlife.notification;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerRepository;
import com.novapetzlife.customer.CustomerService;
import com.novapetzlife.notification.dto.NotificationDto;
import com.novapetzlife.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customer/notifications")
@Tag(name = "Customer Notifications", description = "Customer inbox of order confirmations, dispatch updates, and delivery alerts")
public class NotificationController {

    private final NotificationService notificationService;
    private final CustomerService customerService;
    private final CustomerRepository customerRepository;

    public NotificationController(
            NotificationService notificationService,
            CustomerService customerService,
            CustomerRepository customerRepository) {
        this.notificationService = notificationService;
        this.customerService = customerService;
        this.customerRepository = customerRepository;
    }

    private Customer resolveCustomer(User user, Long customerId) {
        if (user != null) {
            return customerService.getOrCreateCustomerForUser(user);
        }
        if (customerId != null) {
            return customerRepository.findById(customerId).orElse(null);
        }
        return customerRepository.findFirstByOrderByIdAsc().orElse(null);
    }

    @GetMapping
    @Operation(summary = "Get customer notifications", description = "Retrieves recent notifications sent to the customer")
    public ResponseEntity<ApiResponse<List<NotificationDto>>> getCustomerNotifications(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long customerId) {
        Customer customer = resolveCustomer(user, customerId);
        List<NotificationDto> notifications = notificationService.getRecentCustomerNotifications(customer)
                .stream()
                .map(NotificationDto::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(notifications));
    }
}
