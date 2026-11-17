package com.novapetzlife.review.dto;

import jakarta.validation.constraints.NotBlank;

public record ModerateReviewRequest(
        @NotBlank(message = "Status is required (APPROVED or REJECTED)")
        String status,
        String adminReply
) {}
