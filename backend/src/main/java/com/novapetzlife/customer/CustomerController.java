package com.novapetzlife.customer;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customer")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Customer Profile & Addresses", description = "Customer account management and delivery address book")
public class CustomerController {

    private final CustomerService customerService;
    private final UserRepository userRepository;

    public CustomerController(CustomerService customerService, UserRepository userRepository) {
        this.customerService = customerService;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName().toLowerCase().trim())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
    }

    @GetMapping("/profile")
    @Operation(summary = "Get current customer profile", description = "Retrieves customer profile with linked user details and saved address book")
    public ResponseEntity<ApiResponse<CustomerDto>> getProfile(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        CustomerDto profile = customerService.getCustomerProfile(user);
        return ResponseEntity.ok(ApiResponse.ok(profile));
    }

    @GetMapping("/addresses")
    @Operation(summary = "Get saved addresses", description = "Retrieves customer address book ordered by default preference")
    public ResponseEntity<ApiResponse<List<AddressDto>>> getAddresses(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        List<AddressDto> addresses = customerService.getCustomerAddresses(user);
        return ResponseEntity.ok(ApiResponse.ok(addresses));
    }

    @PostMapping("/addresses")
    @Operation(summary = "Add delivery address", description = "Saves a new delivery address for the customer")
    public ResponseEntity<ApiResponse<AddressDto>> addAddress(
            Authentication authentication,
            @Valid @RequestBody AddressCreateRequest request) {
        User user = getAuthenticatedUser(authentication);
        AddressDto saved = customerService.addAddress(user, request);
        return ResponseEntity.ok(ApiResponse.ok("Address added successfully", saved));
    }

    @DeleteMapping("/addresses/{id}")
    @Operation(summary = "Delete delivery address", description = "Removes a saved address from customer address book")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            Authentication authentication,
            @PathVariable Long id) {
        User user = getAuthenticatedUser(authentication);
        customerService.deleteAddress(user, id);
        return ResponseEntity.ok(ApiResponse.ok("Address deleted successfully", null));
    }
}
