package com.novapetzlife.google.dto;

import java.util.List;

public record GoogleBusinessProfileDto(
        String name,
        String address,
        String city,
        String pinCode,
        String placeId,
        Double latitude,
        Double longitude,
        Double overallRating,
        Integer totalUserRatings,
        String googleMapsUrl,
        String phoneNumber,
        Boolean isOpenNow,
        String openingHoursDescription,
        List<GoogleReviewDto> featuredReviews
) {}
