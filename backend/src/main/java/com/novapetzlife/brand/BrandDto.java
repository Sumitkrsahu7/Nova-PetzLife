package com.novapetzlife.brand;

public record BrandDto(
        Long id,
        String name,
        String slug,
        String description,
        String logoUrl,
        Boolean isActive
) {
    public static BrandDto fromEntity(Brand b) {
        return new BrandDto(
                b.getId(),
                b.getName(),
                b.getSlug(),
                b.getDescription(),
                b.getLogoUrl(),
                b.getIsActive()
        );
    }
}
