package com.novapetzlife.petprofile;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerRepository;
import com.novapetzlife.petprofile.dto.AdminPetDetailDto;
import com.novapetzlife.petprofile.dto.AdminPetSummaryDto;
import com.novapetzlife.petprofile.dto.PetProfileDto;
import com.novapetzlife.petprofile.dto.UpdatePetFeedbackRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN')")
@Tag(name = "Admin Pet Profiles", description = "Store management view of customer pet profiles and life-stage nutrition history")
public class AdminPetProfileController {

    private final PetProfileService petProfileService;
    private final CustomerRepository customerRepository;

    public AdminPetProfileController(PetProfileService petProfileService, CustomerRepository customerRepository) {
        this.petProfileService = petProfileService;
        this.customerRepository = customerRepository;
    }

    @GetMapping("/customers/{customerId}/pets")
    @Operation(summary = "Get customer pets (Admin)", description = "Retrieves pets belonging to a specific customer")
    public ResponseEntity<ApiResponse<List<PetProfileDto>>> getCustomerPets(@PathVariable Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with id: " + customerId));
        List<PetProfileDto> pets = petProfileService.getCustomerPetProfiles(customer);
        return ResponseEntity.ok(ApiResponse.ok(pets));
    }

    @GetMapping("/pets")
    @Operation(summary = "List all registered pets (Admin)", description = "Retrieves a paginated list of all customer-registered pets with search and species filter")
    public ResponseEntity<ApiResponse<Page<AdminPetSummaryDto>>> getAllPets(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String species,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AdminPetSummaryDto> pets = petProfileService.getAllPetsForAdmin(pageable, query, species);
        return ResponseEntity.ok(ApiResponse.ok(pets));
    }

    @GetMapping("/pets/{id}")
    @Operation(summary = "Get registered pet details (Admin)", description = "Retrieves complete pet profile and associated owner information")
    public ResponseEntity<ApiResponse<AdminPetDetailDto>> getPetById(@PathVariable Long id) {
        AdminPetDetailDto pet = petProfileService.getPetForAdmin(id);
        return ResponseEntity.ok(ApiResponse.ok(pet));
    }

    @PutMapping("/pets/{id}/feedback")
    @Operation(summary = "Add or update feedback for a pet (Admin)", description = "Publishes or modifies feedback/suggestions for a customer's pet")
    public ResponseEntity<ApiResponse<AdminPetDetailDto>> updateFeedback(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePetFeedbackRequest request) {
        AdminPetDetailDto updated = petProfileService.updatePetFeedback(id, request.getFeedback());
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @DeleteMapping("/pets/{id}/feedback")
    @Operation(summary = "Remove feedback for a pet (Admin)", description = "Clears any previously published feedback for a pet")
    public ResponseEntity<ApiResponse<Void>> deleteFeedback(@PathVariable Long id) {
        petProfileService.deletePetFeedback(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
