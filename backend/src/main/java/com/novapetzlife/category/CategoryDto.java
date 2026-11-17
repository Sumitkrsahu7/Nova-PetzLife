package com.novapetzlife.category;

import java.util.List;

public record CategoryDto(
        Long id,
        Long parentId,
        String name,
        String slug,
        String description,
        String imageUrl,
        Integer displayOrder,
        Boolean isActive,
        List<CategoryDto> subcategories
) {
    public static CategoryDto fromEntity(Category c) {
        List<CategoryDto> subList = c.getSubcategories() != null
                ? c.getSubcategories().stream()
                .filter(sub -> Boolean.TRUE.equals(sub.getIsActive()))
                .map(CategoryDto::fromEntity)
                .toList()
                : List.of();

        return new CategoryDto(
                c.getId(),
                c.getParent() != null ? c.getParent().getId() : null,
                c.getName(),
                c.getSlug(),
                c.getDescription(),
                c.getImageUrl(),
                c.getDisplayOrder(),
                c.getIsActive(),
                subList
        );
    }
}
