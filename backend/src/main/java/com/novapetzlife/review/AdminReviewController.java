package com.novapetzlife.review;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.review.dto.ModerateReviewRequest;
import com.novapetzlife.review.dto.ReviewDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/reviews")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'STORE_MANAGER')")
@Tag(name = "Admin Review Moderation", description = "Administration and moderation workflows for customer product reviews")
public class AdminReviewController {

    private final ReviewService reviewService;

    public AdminReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    @Operation(summary = "List all customer reviews", description = "Retrieves all customer reviews with optional status filtering (ALL, PENDING, APPROVED, REJECTED)")
    public ResponseEntity<ApiResponse<List<ReviewDto>>> getAllReviews(
            @RequestParam(required = false, defaultValue = "ALL") String status) {
        List<ReviewDto> reviews = reviewService.getAllReviewsForAdmin(status);
        return ResponseEntity.ok(ApiResponse.ok(reviews));
    }

    @PatchMapping("/{reviewId}/moderate")
    @Operation(summary = "Moderate review", description = "Approve or reject a review, with optional store response comment")
    public ResponseEntity<ApiResponse<ReviewDto>> moderateReview(
            @PathVariable Long reviewId,
            @Valid @RequestBody ModerateReviewRequest request) {
        ReviewDto review = reviewService.moderateReview(reviewId, request);
        return ResponseEntity.ok(ApiResponse.ok("Review moderation updated", review));
    }

    @DeleteMapping("/{reviewId}")
    @Operation(summary = "Delete review", description = "Permanently removes an inappropriate or spam review")
    public ResponseEntity<ApiResponse<Void>> deleteReview(@PathVariable Long reviewId) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.ok(ApiResponse.ok("Review deleted successfully", null));
    }
}
