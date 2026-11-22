package com.novapetzlife.media.dto;

import com.novapetzlife.product.ProductImage;
import java.time.Instant;

public record ProductImageDto(
        Long id,
        Long productId,
        String imageUrl,
        String altText,
        Integer displayOrder,
        Boolean isPrimary,
        String viewType,
        Instant createdAt
) {
    public static ProductImageDto fromEntity(ProductImage img) {
        return new ProductImageDto(
                img.getId(),
                img.getProduct() != null ? img.getProduct().getId() : null,
                img.getImageUrl(),
                img.getAltText(),
                img.getDisplayOrder(),
                img.getIsPrimary(),
                img.getViewType() != null ? img.getViewType() : (Boolean.TRUE.equals(img.getIsPrimary()) ? "FRONT" : "ADDITIONAL"),
                img.getCreatedAt()
        );
    }
}
