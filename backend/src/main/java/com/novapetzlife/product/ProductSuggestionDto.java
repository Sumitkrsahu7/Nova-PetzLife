package com.novapetzlife.product;

import java.math.BigDecimal;

public record ProductSuggestionDto(
        Long id,
        String name,
        String slug,
        String categoryName,
        String brandName,
        BigDecimal price,
        String imageUrl
) {}
