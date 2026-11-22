package com.novapetzlife.product;

import java.math.BigDecimal;
import java.util.List;

public record ProductDetailDto(
        Long id,
        String name,
        String slug,
        String description,
        String sku,
        String barcode,
        Long brandId,
        String brandName,
        String brandSlug,
        Long categoryId,
        String categoryName,
        String categorySlug,
        BigDecimal basePrice,
        BigDecimal discountedPrice,
        String petType,
        String lifeStage,
        String productType,
        Boolean isActive,
        Boolean isFeatured,
        List<ProductImageDto> images,
        List<ProductVariantDto> variants,
        ReturnPolicyDto returnPolicy,
        Integer availableStock
) {
    public record ProductImageDto(
            Long id,
            String imageUrl,
            String altText,
            Integer displayOrder,
            Boolean isPrimary,
            String viewType
    ) {}

    public record ProductVariantDto(
            Long id,
            String sku,
            String variantName,
            String attributesJson,
            BigDecimal price,
            BigDecimal discountedPrice,
            Integer weightGrams,
            Boolean isActive
    ) {}

    public record ReturnPolicyDto(
            Boolean returnAllowed,
            Integer returnWindowDays,
            Boolean originalPackagingRequired,
            Boolean unusedConditionRequired,
            Boolean refundAllowed,
            String refundMethod,
            String customConditions
    ) {}

    public static ProductDetailDto fromEntity(Product p, Integer availableStock) {
        List<ProductImageDto> imageDtos = p.getImages() != null
                ? p.getImages().stream().map(i -> new ProductImageDto(i.getId(), i.getImageUrl(), i.getAltText(), i.getDisplayOrder(), i.getIsPrimary(), i.getViewType() != null ? i.getViewType() : (Boolean.TRUE.equals(i.getIsPrimary()) ? "FRONT" : "ADDITIONAL"))).toList()
                : List.of();

        List<ProductVariantDto> variantDtos = p.getVariants() != null
                ? p.getVariants().stream().map(v -> new ProductVariantDto(v.getId(), v.getSku(), v.getVariantName(), v.getAttributesJson(), v.getPrice(), v.getDiscountedPrice(), v.getWeightGrams(), v.getIsActive())).toList()
                : List.of();

        ReturnPolicyDto policyDto = null;
        if (p.getReturnPolicy() != null) {
            ProductReturnPolicy pol = p.getReturnPolicy();
            policyDto = new ReturnPolicyDto(
                    pol.getReturnAllowed(),
                    pol.getReturnWindowDays(),
                    pol.getOriginalPackagingRequired(),
                    pol.getUnusedConditionRequired(),
                    pol.getRefundAllowed(),
                    pol.getRefundMethod(),
                    pol.getCustomConditions()
            );
        }

        return new ProductDetailDto(
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getDescription(),
                p.getSku(),
                p.getBarcode(),
                p.getBrand() != null ? p.getBrand().getId() : null,
                p.getBrand() != null ? p.getBrand().getName() : null,
                p.getBrand() != null ? p.getBrand().getSlug() : null,
                p.getCategory() != null ? p.getCategory().getId() : null,
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getCategory() != null ? p.getCategory().getSlug() : null,
                p.getBasePrice(),
                p.getDiscountedPrice(),
                p.getPetType(),
                p.getLifeStage(),
                p.getProductType(),
                p.getIsActive(),
                p.getIsFeatured(),
                imageDtos,
                variantDtos,
                policyDto,
                availableStock != null ? availableStock : 0
        );
    }
}
