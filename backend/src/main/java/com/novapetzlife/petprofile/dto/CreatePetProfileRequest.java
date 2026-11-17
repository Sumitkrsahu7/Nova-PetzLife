package com.novapetzlife.petprofile.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;

public class CreatePetProfileRequest {

    @NotBlank(message = "Pet name is required")
    private String petName;

    @NotBlank(message = "Pet type is required (Dog, Cat, Bird, Fish, Small Animal)")
    private String petType;

    private String breed;
    private LocalDate dateOfBirth;
    private String gender;
    private BigDecimal weightKg;
    private String lifeStage;
    private String dietaryPreferences;
    private String foodSensitivities;

    public CreatePetProfileRequest() {}

    public CreatePetProfileRequest(
            String petName,
            String petType,
            String breed,
            LocalDate dateOfBirth,
            String gender,
            BigDecimal weightKg,
            String lifeStage,
            String dietaryPreferences,
            String foodSensitivities) {
        this.petName = petName;
        this.petType = petType;
        this.breed = breed;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.weightKg = weightKg;
        this.lifeStage = lifeStage;
        this.dietaryPreferences = dietaryPreferences;
        this.foodSensitivities = foodSensitivities;
    }

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
}
