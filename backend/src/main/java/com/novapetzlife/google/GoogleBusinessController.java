package com.novapetzlife.google;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.google.dto.GoogleBusinessProfileDto;
import com.novapetzlife.google.dto.GoogleReviewDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/google-business")
@Tag(name = "Google Business Profile", description = "Verified Google Maps Business Profile, aggregate ratings, and customer reviews for Petzlife Kuravankonam")
public class GoogleBusinessController {

    private final GoogleBusinessService googleBusinessService;

    public GoogleBusinessController(GoogleBusinessService googleBusinessService) {
        this.googleBusinessService = googleBusinessService;
    }

    @GetMapping("/profile")
    @Operation(summary = "Get Google Business Profile details", description = "Retrieves verified store rating, review count, address, and Google Maps URL")
    public ResponseEntity<ApiResponse<GoogleBusinessProfileDto>> getProfile() {
        GoogleBusinessProfileDto profile = googleBusinessService.getBusinessProfile();
        return ResponseEntity.ok(ApiResponse.ok(profile));
    }

    @GetMapping("/reviews")
    @Operation(summary = "Get featured Google reviews", description = "Retrieves high-rated verified Google reviews featured on the homepage")
    public ResponseEntity<ApiResponse<List<GoogleReviewDto>>> getFeaturedReviews() {
        List<GoogleReviewDto> reviews = googleBusinessService.getFeaturedReviews();
        return ResponseEntity.ok(ApiResponse.ok(reviews));
    }
}
