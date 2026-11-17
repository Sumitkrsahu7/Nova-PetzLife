package com.novapetzlife.google.dto;

import com.novapetzlife.google.GoogleReview;
import java.time.Instant;

public record GoogleReviewDto(
        Long id,
        String authorName,
        String authorPhotoUrl,
        Integer rating,
        String relativeTime,
        String reviewText,
        Boolean isFeatured,
        Instant reviewTimestamp
) {
    public static GoogleReviewDto fromEntity(GoogleReview r) {
        return new GoogleReviewDto(
                r.getId(),
                r.getAuthorName(),
                r.getAuthorPhotoUrl(),
                r.getRating(),
                r.getRelativeTimeDescription(),
                r.getReviewText(),
                r.getIsFeatured(),
                r.getReviewTimestamp()
        );
    }
}
