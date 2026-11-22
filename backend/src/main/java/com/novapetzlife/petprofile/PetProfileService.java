package com.novapetzlife.petprofile;

import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerRepository;
import com.novapetzlife.petprofile.dto.AdminPetDetailDto;
import com.novapetzlife.petprofile.dto.AdminPetSummaryDto;
import com.novapetzlife.petprofile.dto.CreatePetProfileRequest;
import com.novapetzlife.petprofile.dto.PetProfileDto;
import com.novapetzlife.petprofile.dto.PetRecommendationDto;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductRepository;
import com.novapetzlife.product.ProductSummaryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class PetProfileService {

    private static final Logger log = LoggerFactory.getLogger(PetProfileService.class);

    private final PetProfileRepository petProfileRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    public PetProfileService(
            PetProfileRepository petProfileRepository,
            CustomerRepository customerRepository,
            ProductRepository productRepository) {
        this.petProfileRepository = petProfileRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public PetProfileDto createPetProfile(Customer customer, CreatePetProfileRequest request) {
        if (customer == null) {
            throw new IllegalArgumentException("Customer is required to create a pet profile");
        }

        PetProfile profile = new PetProfile(
                customer,
                request.getPetName().trim(),
                request.getPetType().trim(),
                request.getBreed() != null ? request.getBreed().trim() : null,
                request.getDateOfBirth(),
                request.getGender(),
                request.getWeightKg(),
                request.getLifeStage() != null ? request.getLifeStage().trim() : "Adult",
                request.getDietaryPreferences(),
                request.getFoodSensitivities()
        );

        PetProfile saved = petProfileRepository.save(profile);
        log.info("Created pet profile {} ({}) for customer {}", saved.getPetName(), saved.getPetType(), customer.getId());
        return PetProfileDto.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<PetProfileDto> getCustomerPetProfiles(Customer customer) {
        if (customer == null) return List.of();
        return petProfileRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId()).stream()
                .map(PetProfileDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public PetProfileDto getPetProfileById(Long id, Customer customer) {
        PetProfile profile = petProfileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pet profile not found with id: " + id));

        if (customer != null && !profile.getCustomer().getId().equals(customer.getId())) {
            throw new IllegalArgumentException("Unauthorized to access this pet profile");
        }

        return PetProfileDto.fromEntity(profile);
    }

    @Transactional
    public PetProfileDto updatePetProfile(Long id, Customer customer, CreatePetProfileRequest req) {
        PetProfile profile = petProfileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pet profile not found with id: " + id));

        if (customer != null && !profile.getCustomer().getId().equals(customer.getId())) {
            throw new IllegalArgumentException("Unauthorized to update this pet profile");
        }

        profile.setPetName(req.getPetName().trim());
        profile.setPetType(req.getPetType().trim());
        profile.setBreed(req.getBreed());
        profile.setDateOfBirth(req.getDateOfBirth());
        profile.setGender(req.getGender());
        profile.setWeightKg(req.getWeightKg());
        profile.setLifeStage(req.getLifeStage());
        profile.setDietaryPreferences(req.getDietaryPreferences());
        profile.setFoodSensitivities(req.getFoodSensitivities());

        return PetProfileDto.fromEntity(petProfileRepository.save(profile));
    }

    @Transactional
    public void deletePetProfile(Long id, Customer customer) {
        PetProfile profile = petProfileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pet profile not found with id: " + id));

        if (customer != null && !profile.getCustomer().getId().equals(customer.getId())) {
            throw new IllegalArgumentException("Unauthorized to delete this pet profile");
        }

        petProfileRepository.delete(profile);
    }

    @Transactional(readOnly = true)
    public PetRecommendationDto getRecommendationsForPet(Long petId) {
        PetProfile profile = petProfileRepository.findById(petId)
                .orElseThrow(() -> new IllegalArgumentException("Pet profile not found with id: " + petId));

        // Find products matching species (e.g. Dog / Cat) and life stage
        List<Product> matched = productRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .filter(p -> profile.getPetType() == null || p.getPetType() == null || profile.getPetType().equalsIgnoreCase(p.getPetType()))
                .filter(p -> profile.getLifeStage() == null || p.getLifeStage() == null || profile.getLifeStage().equalsIgnoreCase(p.getLifeStage()) || "All Stages".equalsIgnoreCase(p.getLifeStage()))
                .limit(6)
                .toList();

        List<ProductSummaryDto> dtos = matched.stream().map(ProductSummaryDto::fromEntity).toList();

        String rationale = String.format("Personalized nutrition & care recommendations for %s (%s, %s) based on Kuravankonam stock",
                profile.getPetName(),
                profile.getBreed() != null ? profile.getBreed() : profile.getPetType(),
                profile.getLifeStage() != null ? profile.getLifeStage() : "All Stages");

        return new PetRecommendationDto(
                profile.getId(),
                profile.getPetName(),
                profile.getPetType(),
                profile.getBreed(),
                profile.getLifeStage(),
                rationale,
                dtos
        );
    }

    @Transactional(readOnly = true)
    public Page<AdminPetSummaryDto> getAllPetsForAdmin(Pageable pageable, String query, String petType) {
        String cleanQuery = (query != null && !query.trim().isEmpty()) ? query.trim() : null;
        String cleanType = (petType != null && !petType.trim().isEmpty() && !"ALL".equalsIgnoreCase(petType.trim())) ? petType.trim() : null;
        return petProfileRepository.searchForAdmin(cleanQuery, cleanType, pageable)
                .map(AdminPetSummaryDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public AdminPetDetailDto getPetForAdmin(Long petId) {
        PetProfile profile = petProfileRepository.findById(petId)
                .orElseThrow(() -> new IllegalArgumentException("Pet profile not found with id: " + petId));
        return AdminPetDetailDto.fromEntity(profile);
    }

    @Transactional
    public AdminPetDetailDto updatePetFeedback(Long petId, String feedback) {
        PetProfile profile = petProfileRepository.findById(petId)
                .orElseThrow(() -> new IllegalArgumentException("Pet profile not found with id: " + petId));

        if (feedback == null || feedback.trim().isEmpty()) {
            profile.setAdminFeedback(null);
            profile.setAdminFeedbackUpdatedAt(null);
        } else {
            String trimmed = feedback.trim();
            if (trimmed.length() > 2000) {
                throw new IllegalArgumentException("Feedback cannot exceed 2000 characters");
            }
            profile.setAdminFeedback(trimmed);
            profile.setAdminFeedbackUpdatedAt(Instant.now());
        }

        PetProfile saved = petProfileRepository.save(profile);
        log.info("Updated admin feedback for pet {} ({})", saved.getId(), saved.getPetName());
        return AdminPetDetailDto.fromEntity(saved);
    }

    @Transactional
    public void deletePetFeedback(Long petId) {
        PetProfile profile = petProfileRepository.findById(petId)
                .orElseThrow(() -> new IllegalArgumentException("Pet profile not found with id: " + petId));

        profile.setAdminFeedback(null);
        profile.setAdminFeedbackUpdatedAt(null);
        petProfileRepository.save(profile);
        log.info("Cleared admin feedback for pet {} ({})", profile.getId(), profile.getPetName());
    }
}
