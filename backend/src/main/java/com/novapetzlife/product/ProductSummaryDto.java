package com.novapetzlife.product;

import java.math.BigDecimal;
import java.util.List;

public record ProductSummaryDto(
        Long id,
        String name,
        String slug,
        String sku,
        String brandName,
        String brandSlug,
        String categoryName,
        String categorySlug,
        BigDecimal basePrice,
        BigDecimal discountedPrice,
        String petType,
        String lifeStage,
        String primaryImageUrl,
        Boolean inStock,
        Boolean isFeatured,
        Boolean returnAllowed,
        List<VariantSummaryDto> variants
) {
    public record VariantSummaryDto(
            Long id,
            String variantName,
            String sku,
            BigDecimal price,
            BigDecimal discountedPrice,
            Integer weightGrams
    ) {}

    public static ProductSummaryDto fromEntity(Product p) {
        return fromEntity(p, true);
    }

    public static ProductSummaryDto fromEntity(Product p, boolean inStock) {
        String primaryImage = p.getImages() != null && !p.getImages().isEmpty()
                ? p.getImages().stream().filter(ProductImage::getIsPrimary).findFirst()
                .map(ProductImage::getImageUrl)
                .orElse(p.getImages().get(0).getImageUrl())
                : null;

        List<VariantSummaryDto> variantDtos = p.getVariants() != null
                ? p.getVariants().stream()
                .filter(v -> Boolean.TRUE.equals(v.getIsActive()))
                .map(v -> new VariantSummaryDto(v.getId(), v.getVariantName(), v.getSku(), v.getPrice(), v.getDiscountedPrice(), v.getWeightGrams()))
                .toList()
                : List.of();

        boolean returnAllowed = p.getReturnPolicy() != null && Boolean.TRUE.equals(p.getReturnPolicy().getReturnAllowed());

        return new ProductSummaryDto(
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getSku(),
                p.getBrand() != null ? p.getBrand().getName() : null,
                p.getBrand() != null ? p.getBrand().getSlug() : null,
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getCategory() != null ? p.getCategory().getSlug() : null,
                p.getBasePrice(),
                p.getDiscountedPrice(),
                p.getPetType(),
                p.getLifeStage(),
                primaryImage,
                inStock,
                p.getIsFeatured(),
                returnAllowed,
                variantDtos
        );
    }
}
