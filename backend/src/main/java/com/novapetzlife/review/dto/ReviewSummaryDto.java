package com.novapetzlife.review.dto;

import java.util.Map;

public record ReviewSummaryDto(
        Long productId,
        Double averageRating,
        Long totalReviews,
        Map<Integer, Long> ratingDistribution
) {}
