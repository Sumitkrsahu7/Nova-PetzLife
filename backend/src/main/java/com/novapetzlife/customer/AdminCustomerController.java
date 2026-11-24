package com.novapetzlife.customer;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/customers")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN')")
@Tag(name = "Customer Management (Admin)", description = "View and manage registered and guest customer accounts")
public class AdminCustomerController {

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;
    private final UserRepository userRepository;

    public AdminCustomerController(
            CustomerRepository customerRepository,
            CustomerAddressRepository addressRepository,
            UserRepository userRepository) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Operation(summary = "Search customers with pagination")
    public ResponseEntity<ApiResponse<Page<CustomerDto>>> getCustomers(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Customer> customers = customerRepository.searchCustomers(query, pageable);
        return ResponseEntity.ok(ApiResponse.ok(customers.map(CustomerDto::fromEntity)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get customer details by id")
    public ResponseEntity<ApiResponse<CustomerDto>> getCustomerById(@PathVariable Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with id: " + id));

        // Load addresses
        var addresses = addressRepository.findByCustomerIdOrderByIsDefaultDescCreatedAtDesc(id)
                .stream().map(AddressDto::fromEntity).toList();

        CustomerDto dto = new CustomerDto(
                customer.getId(),
                customer.getUser() != null ? customer.getUser().getId() : null,
                customer.getEmail(),
                customer.getPhone(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getIsGuest(),
                addresses
        );

        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Toggle customer account active status")
    public ResponseEntity<ApiResponse<String>> toggleCustomerStatus(@PathVariable Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with id: " + id));

        if (customer.getUser() != null) {
            User user = customer.getUser();
            boolean isSuperAdmin = user.getRoles().stream().anyMatch(r -> "ROLE_SUPER_ADMIN".equalsIgnoreCase(r.getName()));
            if (isSuperAdmin) {
                throw new IllegalArgumentException("Super Admin user status cannot be modified via customer management endpoint.");
            }
            boolean current = Boolean.TRUE.equals(user.getIsActive());
            user.setIsActive(!current);
            userRepository.save(user);
            return ResponseEntity.ok(ApiResponse.ok("Customer active status set to: " + (!current)));
        }

        return ResponseEntity.ok(ApiResponse.ok("Guest customer has no linked login account to toggle"));
    }
}
