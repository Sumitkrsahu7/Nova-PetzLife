package com.novapetzlife.google;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.google.dto.GoogleReviewDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/google-business")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'STORE_MANAGER')")
@Tag(name = "Admin Google Business Profile", description = "Administration and syncing of Google Business Profile customer reviews")
public class AdminGoogleBusinessController {

    private final GoogleBusinessService googleBusinessService;

    public AdminGoogleBusinessController(GoogleBusinessService googleBusinessService) {
        this.googleBusinessService = googleBusinessService;
    }

    @GetMapping("/reviews")
    @Operation(summary = "List all synced Google reviews", description = "Retrieves all synced Google reviews with featured status")
    public ResponseEntity<ApiResponse<List<GoogleReviewDto>>> getAllReviews() {
        List<GoogleReviewDto> reviews = googleBusinessService.getAllGoogleReviews();
        return ResponseEntity.ok(ApiResponse.ok(reviews));
    }

    @PostMapping("/sync")
    @Operation(summary = "Sync reviews from Google Places API", description = "Triggers a sync of recent reviews and rating updates from Google Places")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncReviews() {
        int count = googleBusinessService.syncGoogleReviews();
        return ResponseEntity.ok(ApiResponse.ok("Google Business reviews synchronized successfully", Map.of("syncedCount", count)));
    }

    @PatchMapping("/reviews/{reviewId}/feature")
    @Operation(summary = "Toggle homepage feature status", description = "Enables or disables display of a specific Google review on the store homepage")
    public ResponseEntity<ApiResponse<GoogleReviewDto>> toggleFeature(
            @PathVariable Long reviewId,
            @RequestParam boolean isFeatured) {
        GoogleReviewDto updated = googleBusinessService.toggleFeatured(reviewId, isFeatured);
        return ResponseEntity.ok(ApiResponse.ok("Review display status updated", updated));
    }
}
