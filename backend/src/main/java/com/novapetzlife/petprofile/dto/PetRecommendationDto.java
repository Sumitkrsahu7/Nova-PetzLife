package com.novapetzlife.petprofile.dto;

import com.novapetzlife.product.ProductSummaryDto;
import java.util.List;

public class PetRecommendationDto {

    private Long petId;
    private String petName;
    private String petType;
    private String breed;
    private String lifeStage;
    private String rationale;
    private List<ProductSummaryDto> recommendedProducts;

    public PetRecommendationDto() {}

    public PetRecommendationDto(Long petId, String petName, String petType, String breed, String lifeStage, String rationale, List<ProductSummaryDto> recommendedProducts) {
        this.petId = petId;
        this.petName = petName;
        this.petType = petType;
        this.breed = breed;
        this.lifeStage = lifeStage;
        this.rationale = rationale;
        this.recommendedProducts = recommendedProducts;
    }

    public Long getPetId() { return petId; }
    public void setPetId(Long petId) { this.petId = petId; }
    public String getPetName() { return petName; }
    public void setPetName(String petName) { this.petName = petName; }
    public String getPetType() { return petType; }
    public void setPetType(String petType) { this.petType = petType; }
    public String getBreed() { return breed; }
    public void setBreed(String breed) { this.breed = breed; }
    public String getLifeStage() { return lifeStage; }
    public void setLifeStage(String lifeStage) { this.lifeStage = lifeStage; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public List<ProductSummaryDto> getRecommendedProducts() { return recommendedProducts; }
    public void setRecommendedProducts(List<ProductSummaryDto> recommendedProducts) { this.recommendedProducts = recommendedProducts; }
}
