package com.novapetzlife.review.dto;

import com.novapetzlife.review.Review;
import com.novapetzlife.review.ReviewImage;
import java.time.Instant;
import java.util.List;

public record ReviewDto(
        Long id,
        Long productId,
        String productName,
        Long customerId,
        String customerName,
        Integer rating,
        String title,
        String comment,
        boolean isVerifiedPurchase,
        String status,
        String adminReply,
        List<String> imageUrls,
        Instant createdAt
) {
    public static ReviewDto fromEntity(Review r) {
        String customerName = r.getCustomer() != null
                ? (r.getCustomer().getFirstName() + " " + (r.getCustomer().getLastName() != null ? r.getCustomer().getLastName() : "")).trim()
                : "Anonymous Pet Parent";
        String productName = r.getProduct() != null ? r.getProduct().getName() : "";
        List<String> images = r.getImages() != null
                ? r.getImages().stream().map(ReviewImage::getImageUrl).toList()
                : List.of();

        return new ReviewDto(
                r.getId(),
                r.getProduct() != null ? r.getProduct().getId() : null,
                productName,
                r.getCustomer() != null ? r.getCustomer().getId() : null,
                customerName,
                r.getRating(),
                r.getTitle(),
                r.getComment(),
                Boolean.TRUE.equals(r.getIsVerifiedPurchase()),
                r.getStatus(),
                r.getAdminReply(),
                images,
                r.getCreatedAt()
        );
    }
}
