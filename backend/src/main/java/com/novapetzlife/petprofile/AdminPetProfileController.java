package com.novapetzlife.petprofile;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerRepository;
import com.novapetzlife.petprofile.dto.PetProfileDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/customers/{customerId}/pets")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN')")
@Tag(name = "Admin Pet Profiles", description = "Store management view of customer pet profiles and life-stage nutrition history")
public class AdminPetProfileController {

    private final PetProfileService petProfileService;
    private final CustomerRepository customerRepository;

    public AdminPetProfileController(PetProfileService petProfileService, CustomerRepository customerRepository) {
        this.petProfileService = petProfileService;
        this.customerRepository = customerRepository;
    }

    @GetMapping
    @Operation(summary = "Get customer pets (Admin)", description = "Retrieves pets belonging to a specific customer")
    public ResponseEntity<ApiResponse<List<PetProfileDto>>> getCustomerPets(@PathVariable Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with id: " + customerId));
        List<PetProfileDto> pets = petProfileService.getCustomerPetProfiles(customer);
        return ResponseEntity.ok(ApiResponse.ok(pets));
    }
}
