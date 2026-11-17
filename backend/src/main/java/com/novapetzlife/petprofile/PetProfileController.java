package com.novapetzlife.petprofile;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerRepository;
import com.novapetzlife.customer.CustomerService;
import com.novapetzlife.petprofile.dto.CreatePetProfileRequest;
import com.novapetzlife.petprofile.dto.PetProfileDto;
import com.novapetzlife.petprofile.dto.PetRecommendationDto;
import com.novapetzlife.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customer/pets")
@Tag(name = "Customer Pet Profiles", description = "Management of pets, breeds, life stages, dietary sensitivities, and personalized nutritional recommendations")
public class PetProfileController {

    private final PetProfileService petProfileService;
    private final CustomerService customerService;
    private final CustomerRepository customerRepository;

    public PetProfileController(
            PetProfileService petProfileService,
            CustomerService customerService,
            CustomerRepository customerRepository) {
        this.petProfileService = petProfileService;
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
        // Fallback: first available customer for anonymous demo or guest testing
        return customerRepository.findFirstByOrderByIdAsc().orElse(null);
    }

    @GetMapping
    @Operation(summary = "List customer pets", description = "Retrieves all registered pet profiles for customer")
    public ResponseEntity<ApiResponse<List<PetProfileDto>>> getPets(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long customerId) {
        Customer customer = resolveCustomer(user, customerId);
        List<PetProfileDto> pets = petProfileService.getCustomerPetProfiles(customer);
        return ResponseEntity.ok(ApiResponse.ok(pets));
    }

    @PostMapping
    @Operation(summary = "Register new pet profile", description = "Adds pet details, breed, birthdate, life stage, and sensitivities")
    public ResponseEntity<ApiResponse<PetProfileDto>> createPet(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long customerId,
            @Valid @RequestBody CreatePetProfileRequest request) {
        Customer customer = resolveCustomer(user, customerId);
        if (customer == null) {
            customer = customerService.createGuestCustomer("petowner@example.com", "+919447100000", "Pet", "Owner");
        }
        PetProfileDto created = petProfileService.createPetProfile(customer, request);
        return ResponseEntity.ok(ApiResponse.ok(created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get pet profile by ID", description = "Retrieves a single pet profile")
    public ResponseEntity<ApiResponse<PetProfileDto>> getPet(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long customerId) {
        Customer customer = resolveCustomer(user, customerId);
        PetProfileDto pet = petProfileService.getPetProfileById(id, customer);
        return ResponseEntity.ok(ApiResponse.ok(pet));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update pet profile", description = "Modifies existing pet parameters")
    public ResponseEntity<ApiResponse<PetProfileDto>> updatePet(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long customerId,
            @Valid @RequestBody CreatePetProfileRequest request) {
        Customer customer = resolveCustomer(user, customerId);
        PetProfileDto updated = petProfileService.updatePetProfile(id, customer, request);
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete pet profile", description = "Removes a pet profile")
    public ResponseEntity<ApiResponse<Void>> deletePet(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long customerId) {
        Customer customer = resolveCustomer(user, customerId);
        petProfileService.deletePetProfile(id, customer);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/{id}/recommendations")
    @Operation(summary = "Get personalized pet recommendations", description = "Automated food, supplement, and accessory suggestions based on pet breed, life stage, and sensitivities")
    public ResponseEntity<ApiResponse<PetRecommendationDto>> getRecommendations(@PathVariable Long id) {
        PetRecommendationDto recs = petProfileService.getRecommendationsForPet(id);
        return ResponseEntity.ok(ApiResponse.ok(recs));
    }
}
