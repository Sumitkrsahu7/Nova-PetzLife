package com.novapetzlife.petprofile;

import com.novapetzlife.customer.Customer;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "pet_profiles")
public class PetProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "pet_name", nullable = false, length = 100)
    private String petName;

    @Column(name = "pet_type", nullable = false, length = 50)
    private String petType; // Dog, Cat, Bird, Fish, Small Animal

    @Column(length = 100)
    private String breed;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 10)
    private String gender; // Male, Female

    @Column(name = "weight_kg", precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "life_stage", length = 50)
    private String lifeStage; // Puppy, Kitten, Adult, Senior

    @Column(name = "dietary_preferences", columnDefinition = "TEXT")
    private String dietaryPreferences;

    @Column(name = "food_sensitivities", columnDefinition = "TEXT")
    private String foodSensitivities;

    @Column(name = "admin_feedback", columnDefinition = "TEXT")
    private String adminFeedback;

    @Column(name = "admin_feedback_updated_at")
    private Instant adminFeedbackUpdatedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public PetProfile() {}

    public PetProfile(
            Customer customer,
            String petName,
            String petType,
            String breed,
            LocalDate dateOfBirth,
            String gender,
            BigDecimal weightKg,
            String lifeStage,
            String dietaryPreferences,
            String foodSensitivities) {
        this.customer = customer;
        this.petName = petName;
        this.petType = petType;
        this.breed = breed;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.weightKg = weightKg;
        this.lifeStage = lifeStage;
        this.dietaryPreferences = dietaryPreferences;
        this.foodSensitivities = foodSensitivities;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
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
    public String getAdminFeedback() { return adminFeedback; }
    public void setAdminFeedback(String adminFeedback) { this.adminFeedback = adminFeedback; }
    public Instant getAdminFeedbackUpdatedAt() { return adminFeedbackUpdatedAt; }
    public void setAdminFeedbackUpdatedAt(Instant adminFeedbackUpdatedAt) { this.adminFeedbackUpdatedAt = adminFeedbackUpdatedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
