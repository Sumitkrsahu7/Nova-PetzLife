package com.novapetzlife.petprofile.dto;

import com.novapetzlife.petprofile.PetProfile;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public class PetProfileDto {

    private Long id;
    private Long customerId;
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

    public PetProfileDto() {}

    public static PetProfileDto fromEntity(PetProfile p) {
        PetProfileDto dto = new PetProfileDto();
        dto.setId(p.getId());
        dto.setCustomerId(p.getCustomer().getId());
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
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
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
}
