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

    public ReviewController(
            ReviewService reviewService,
            CustomerService customerService,
            CustomerRepository customerRepository) {
        this.reviewService = reviewService;
        this.customerService = customerService;
        this.customerRepository = customerRepository;
    }

    private Customer resolveCustomer(User user, Long customerId) {
        if (user != null) {
            return customerService.getOrCreateCustomerForUser(user);
        }
        if (customerId != null) {
            return customerRepository.findById(customerId).orElse(null);
        }
        return null;
    }

    @PostMapping
    @Operation(summary = "Submit product review", description = "Submits a customer rating (1-5 stars) and review comment, validating verified purchaser status")
    public ResponseEntity<ApiResponse<ReviewDto>> submitReview(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long customerId,
            @Valid @RequestBody CreateReviewRequest request) {
        Customer customer = resolveCustomer(user, customerId);
        if (customer == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Customer account required to submit a review"));
        }
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
