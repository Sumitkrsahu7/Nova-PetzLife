package com.novapetzlife.google;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GoogleReviewRepository extends JpaRepository<GoogleReview, Long> {

    List<GoogleReview> findByIsFeaturedTrueOrderByReviewTimestampDesc();

    List<GoogleReview> findAllByOrderByReviewTimestampDesc();

    Optional<GoogleReview> findByGoogleReviewId(String googleReviewId);
}
