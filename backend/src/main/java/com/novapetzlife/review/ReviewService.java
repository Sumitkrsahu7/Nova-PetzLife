package com.novapetzlife.review;

import com.novapetzlife.customer.Customer;
import com.novapetzlife.order.OrderItemRepository;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductRepository;
import com.novapetzlife.review.dto.CreateReviewRequest;
import com.novapetzlife.review.dto.ModerateReviewRequest;
import com.novapetzlife.review.dto.ReviewDto;
import com.novapetzlife.review.dto.ReviewSummaryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final com.novapetzlife.audit.AuditService auditService;

    public ReviewService(
            ReviewRepository reviewRepository,
            ProductRepository productRepository,
            OrderItemRepository orderItemRepository,
            com.novapetzlife.audit.AuditService auditService) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.auditService = auditService;
    }

    @Transactional
    public ReviewDto submitReview(Customer customer, CreateReviewRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + request.productId()));

        if (reviewRepository.existsByProductIdAndCustomerId(product.getId(), customer.getId())) {
            throw new IllegalArgumentException("Customer has already submitted a review for this product");
        }

        boolean isVerified = orderItemRepository.isVerifiedPurchase(customer.getId(), product.getId());

        Review review = new Review(
                product,
                customer,
                request.rating(),
                request.title(),
                request.comment(),
                isVerified,
                "APPROVED" // Auto-approved for customer convenience, with administrative moderation capabilities
        );

        if (request.imageUrls() != null && !request.imageUrls().isEmpty()) {
            for (String url : request.imageUrls()) {
                if (url != null && !url.isBlank()) {
                    review.addImage(url.trim());
                }
            }
        }

        review = reviewRepository.save(review);
        log.info("Customer {} submitted a {}-star review for product '{}' (Verified: {})",
                customer.getId(), request.rating(), product.getName(), isVerified);

        return ReviewDto.fromEntity(review);
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> getProductReviews(Long productId) {
        return reviewRepository.findByProductIdAndStatusOrderByCreatedAtDesc(productId, "APPROVED")
                .stream()
                .map(ReviewDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReviewSummaryDto getProductReviewSummary(Long productId) {
        Double avgRating = reviewRepository.findAverageRatingByProductId(productId);
        long totalCount = reviewRepository.countByProductIdAndStatus(productId, "APPROVED");

        Map<Integer, Long> distribution = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            distribution.put(i, 0L);
        }

        List<Object[]> rows = reviewRepository.countRatingsByStar(productId);
        for (Object[] row : rows) {
            Integer star = ((Number) row[0]).intValue();
            Long count = ((Number) row[1]).longValue();
            distribution.put(star, count);
        }

        double roundedAvg = avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0;
        return new ReviewSummaryDto(productId, roundedAvg, totalCount, distribution);
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> getAllReviewsForAdmin(String status) {
        List<Review> reviews;
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            reviews = reviewRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase());
        } else {
            reviews = reviewRepository.findAllByOrderByCreatedAtDesc();
        }
        return reviews.stream().map(ReviewDto::fromEntity).toList();
    }

    @Transactional
    public ReviewDto moderateReview(Long reviewId, ModerateReviewRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found with id: " + reviewId));

        review.setStatus(request.status().toUpperCase());
        if (request.adminReply() != null) {
            review.setAdminReply(request.adminReply().trim());
        }
        review.setUpdatedAt(Instant.now());

        review = reviewRepository.save(review);
        log.info("Admin moderated review {} to status: {}", reviewId, review.getStatus());
        if (auditService != null) {
            auditService.recordAudit("Review", String.valueOf(reviewId), "REVIEW_MODERATED", "ADMIN", null, review.getStatus(), null);
        }
        return ReviewDto.fromEntity(review);
    }

    @Transactional
    public void deleteReview(Long reviewId) {
        if (!reviewRepository.existsById(reviewId)) {
            throw new IllegalArgumentException("Review not found with id: " + reviewId);
        }
        reviewRepository.deleteById(reviewId);
        log.info("Admin deleted review {}", reviewId);
    }
}
