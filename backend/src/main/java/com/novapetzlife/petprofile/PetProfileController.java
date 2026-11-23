package com.novapetzlife.petprofile;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerService;
import com.novapetzlife.petprofile.dto.CreatePetProfileRequest;
import com.novapetzlife.petprofile.dto.PetProfileDto;
import com.novapetzlife.petprofile.dto.PetRecommendationDto;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customer/pets")
@Tag(name = "Customer Pet Profiles", description = "Management of pets, breeds, life stages, dietary sensitivities, and personalized nutritional recommendations")
public class PetProfileController {

    private final PetProfileService petProfileService;
    private final CustomerService customerService;
    private final UserRepository userRepository;

    public PetProfileController(
            PetProfileService petProfileService,
            CustomerService customerService,
            UserRepository userRepository) {
        this.petProfileService = petProfileService;
        this.customerService = customerService;
        this.userRepository = userRepository;
    }

    private Customer resolveAuthenticatedCustomer(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new AccessDeniedException("Authentication required to access customer pet profiles");
        }
        User user = userRepository.findByEmail(authentication.getName().toLowerCase().trim())
                .orElseThrow(() -> new AccessDeniedException("Customer account not found"));
        return customerService.getOrCreateCustomerForUser(user);
    }

    @GetMapping
    @Operation(summary = "List customer pets", description = "Retrieves all registered pet profiles for the authenticated customer")
    public ResponseEntity<ApiResponse<List<PetProfileDto>>> getPets(Authentication authentication) {
        Customer customer = resolveAuthenticatedCustomer(authentication);
        List<PetProfileDto> pets = petProfileService.getCustomerPetProfiles(customer);
        return ResponseEntity.ok(ApiResponse.ok(pets));
    }

    @PostMapping
    @Operation(summary = "Register new pet profile", description = "Adds pet details, breed, birthdate, life stage, and sensitivities for authenticated customer")
    public ResponseEntity<ApiResponse<PetProfileDto>> createPet(
            Authentication authentication,
            @Valid @RequestBody CreatePetProfileRequest request) {
        Customer customer = resolveAuthenticatedCustomer(authentication);
        PetProfileDto created = petProfileService.createPetProfile(customer, request);
        return ResponseEntity.ok(ApiResponse.ok(created));
    }

    @PostMapping("/guest")
    @Operation(summary = "Register new pet profile for guest onboarding", description = "Isolated guest pet registration that does not expose customer records")
    public ResponseEntity<ApiResponse<PetProfileDto>> createGuestPet(
            @Valid @RequestBody CreatePetProfileRequest request) {
        // Create an isolated guest customer record that cannot be claimed without verified identity
        Customer guest = customerService.createGuestCustomer(
                "guest_" + UUID.randomUUID().toString().substring(0, 8) + "@guest.novapetzlife.com",
                "+919447100000",
                "Guest",
                "Owner"
        );
        PetProfileDto created = petProfileService.createPetProfile(guest, request);
        return ResponseEntity.ok(ApiResponse.ok("Pet profile registered for guest onboarding", created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get pet profile by ID", description = "Retrieves a single pet profile owned by the authenticated customer")
    public ResponseEntity<ApiResponse<PetProfileDto>> getPet(
            @PathVariable Long id,
            Authentication authentication) {
        Customer customer = resolveAuthenticatedCustomer(authentication);
        PetProfileDto pet = petProfileService.getPetProfileById(id, customer);
        return ResponseEntity.ok(ApiResponse.ok(pet));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update pet profile", description = "Modifies existing pet parameters for authenticated customer's pet")
    public ResponseEntity<ApiResponse<PetProfileDto>> updatePet(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody CreatePetProfileRequest request) {
        Customer customer = resolveAuthenticatedCustomer(authentication);
        PetProfileDto updated = petProfileService.updatePetProfile(id, customer, request);
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete pet profile", description = "Removes a pet profile owned by the authenticated customer")
    public ResponseEntity<ApiResponse<Void>> deletePet(
            @PathVariable Long id,
            Authentication authentication) {
        Customer customer = resolveAuthenticatedCustomer(authentication);
        petProfileService.deletePetProfile(id, customer);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/{id}/recommendations")
    @Operation(summary = "Get personalized pet recommendations", description = "Automated food, supplement, and accessory suggestions based on pet breed, life stage, and sensitivities")
    public ResponseEntity<ApiResponse<PetRecommendationDto>> getRecommendations(
            @PathVariable Long id,
            Authentication authentication) {
        Customer customer = null;
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            User user = userRepository.findByEmail(authentication.getName().toLowerCase().trim()).orElse(null);
            if (user != null) {
                customer = customerService.getOrCreateCustomerForUser(user);
            }
        }
        PetRecommendationDto recs = petProfileService.getRecommendationsForPet(id, customer);
        return ResponseEntity.ok(ApiResponse.ok(recs));
    }
}
