package com.novapetzlife.customer;

import com.novapetzlife.auth.AuthResponse;
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
import java.util.Map;

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

    @PutMapping("/email")
    @Operation(summary = "Update customer email address", description = "Updates email on user and customer records and returns refreshed JWT credentials")
    public ResponseEntity<ApiResponse<AuthResponse>> updateEmail(
            Authentication authentication,
            @RequestBody Map<String, String> request) {
        User user = getAuthenticatedUser(authentication);
        String newEmail = request.get("email");
        String currentPassword = request.get("currentPassword");
        String googleIdToken = request.get("googleIdToken");
        if (googleIdToken == null) {
            googleIdToken = request.get("idToken");
        }
        if (newEmail == null || newEmail.trim().isEmpty() || !newEmail.contains("@")) {
            throw new IllegalArgumentException("A valid email address is required");
        }
        AuthResponse response = customerService.updateEmail(user, newEmail, currentPassword, googleIdToken);
        return ResponseEntity.ok(ApiResponse.ok("Email address updated successfully", response));
    }

    @DeleteMapping("/account")
    @Operation(summary = "Delete customer account", description = "Permanently deactivates account and clears customer identity")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            Authentication authentication,
            @RequestBody(required = false) Map<String, String> request) {
        User user = getAuthenticatedUser(authentication);
        String currentPassword = request != null ? request.get("currentPassword") : null;
        String googleIdToken = request != null ? request.get("googleIdToken") : null;
        if (googleIdToken == null && request != null) {
            googleIdToken = request.get("idToken");
        }
        customerService.deleteAccount(user, currentPassword, googleIdToken);
        return ResponseEntity.ok(ApiResponse.ok("Account deleted successfully", null));
    }
}
