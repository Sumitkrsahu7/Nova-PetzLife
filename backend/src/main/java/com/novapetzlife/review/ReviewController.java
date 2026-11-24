package com.novapetzlife.review;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerRepository;
import com.novapetzlife.customer.CustomerService;
import com.novapetzlife.review.dto.CreateReviewRequest;
import com.novapetzlife.review.dto.ReviewDto;
import com.novapetzlife.review.dto.ReviewSummaryDto;
import com.novapetzlife.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
@Tag(name = "Customer Product Reviews", description = "Customer star ratings, written reviews, verified purchase verification, and review summaries")
public class ReviewController {

    private final ReviewService reviewService;
    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final com.novapetzlife.user.UserRepository userRepository;

    public ReviewController(
            ReviewService reviewService,
            CustomerService customerService,
            CustomerRepository customerRepository,
            com.novapetzlife.user.UserRepository userRepository) {
        this.reviewService = reviewService;
        this.customerService = customerService;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    @Operation(summary = "Submit product review", description = "Submits a customer rating (1-5 stars) and review comment, validating verified purchaser status")
    public ResponseEntity<ApiResponse<ReviewDto>> submitReview(
            org.springframework.security.core.Authentication authentication,
            @Valid @RequestBody CreateReviewRequest request) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new org.springframework.security.access.AccessDeniedException("Customer authentication required to submit a review");
        }
        User user = userRepository.findByEmail(authentication.getName().toLowerCase().trim())
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Customer authentication required to submit a review"));
        Customer customer = customerService.getOrCreateCustomerForUser(user);
        ReviewDto review = reviewService.submitReview(customer, request);
        return ResponseEntity.ok(ApiResponse.ok("Review submitted successfully", review));
    }

    @GetMapping("/product/{productId}")
    @Operation(summary = "Get product reviews", description = "Retrieves all approved customer reviews for a specific product")
    public ResponseEntity<ApiResponse<List<ReviewDto>>> getProductReviews(@PathVariable Long productId) {
        List<ReviewDto> reviews = reviewService.getProductReviews(productId);
        return ResponseEntity.ok(ApiResponse.ok(reviews));
    }

    @GetMapping("/product/{productId}/summary")
    @Operation(summary = "Get product rating summary", description = "Calculates average star rating, total review count, and star distribution")
    public ResponseEntity<ApiResponse<ReviewSummaryDto>> getProductReviewSummary(@PathVariable Long productId) {
        ReviewSummaryDto summary = reviewService.getProductReviewSummary(productId);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }
}
