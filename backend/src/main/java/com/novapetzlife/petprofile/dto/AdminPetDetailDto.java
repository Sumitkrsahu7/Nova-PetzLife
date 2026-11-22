package com.novapetzlife.petprofile.dto;

import com.novapetzlife.customer.Customer;
import com.novapetzlife.petprofile.PetProfile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public class AdminPetDetailDto {

    private Long id;
    private String petName;
    private String petType;
    private String breed;
    private LocalDate dateOfBirth;
    private String gender;
    private BigDecimal weightKg;
    private String lifeStage;
    private String dietaryPreferences;
    private String foodSensitivities;
    private Instant createdAt;
    private Instant updatedAt;

    private Long customerId;
    private String ownerName;
    private String ownerEmail;
    private String ownerPhone;

    private boolean hasFeedback;
    private String adminFeedback;
    private Instant adminFeedbackUpdatedAt;

    public AdminPetDetailDto() {}

    public static AdminPetDetailDto fromEntity(PetProfile p) {
        AdminPetDetailDto dto = new AdminPetDetailDto();
        dto.setId(p.getId());
        dto.setPetName(p.getPetName());
        dto.setPetType(p.getPetType());
        dto.setBreed(p.getBreed());
        dto.setDateOfBirth(p.getDateOfBirth());
        dto.setGender(p.getGender());
        dto.setWeightKg(p.getWeightKg());
        dto.setLifeStage(p.getLifeStage());
        dto.setDietaryPreferences(p.getDietaryPreferences());
        dto.setFoodSensitivities(p.getFoodSensitivities());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setUpdatedAt(p.getUpdatedAt());

        Customer c = p.getCustomer();
        if (c != null) {
            dto.setCustomerId(c.getId());
            String fullName = ((c.getFirstName() != null ? c.getFirstName() : "") + " " +
                               (c.getLastName() != null ? c.getLastName() : "")).trim();
            dto.setOwnerName(fullName.isEmpty() ? "Unknown Owner" : fullName);
            dto.setOwnerEmail(c.getEmail());
            dto.setOwnerPhone(c.getPhone());
        }

        String fb = p.getAdminFeedback();
        dto.setHasFeedback(fb != null && !fb.isBlank());
        dto.setAdminFeedback(fb);
        dto.setAdminFeedbackUpdatedAt(p.getAdminFeedbackUpdatedAt());
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPetName() { return petName; }
    public void setPetName(String petName) { this.petName = petName; }
    public String getPetType() { return petType; }
    public void setPetType(String petType) { this.petType = petType; }
    public String getBreed() { return breed; }
    public void setBreed(String breed) { this.breed = breed; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
    public String getLifeStage() { return lifeStage; }
    public void setLifeStage(String lifeStage) { this.lifeStage = lifeStage; }
    public String getDietaryPreferences() { return dietaryPreferences; }
    public void setDietaryPreferences(String dietaryPreferences) { this.dietaryPreferences = dietaryPreferences; }
    public String getFoodSensitivities() { return foodSensitivities; }
    public void setFoodSensitivities(String foodSensitivities) { this.foodSensitivities = foodSensitivities; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }
    public String getOwnerPhone() { return ownerPhone; }
    public void setOwnerPhone(String ownerPhone) { this.ownerPhone = ownerPhone; }
    public boolean isHasFeedback() { return hasFeedback; }
    public void setHasFeedback(boolean hasFeedback) { this.hasFeedback = hasFeedback; }
    public String getAdminFeedback() { return adminFeedback; }
    public void setAdminFeedback(String adminFeedback) { this.adminFeedback = adminFeedback; }
    public Instant getAdminFeedbackUpdatedAt() { return adminFeedbackUpdatedAt; }
    public void setAdminFeedbackUpdatedAt(Instant adminFeedbackUpdatedAt) { this.adminFeedbackUpdatedAt = adminFeedbackUpdatedAt; }
}
