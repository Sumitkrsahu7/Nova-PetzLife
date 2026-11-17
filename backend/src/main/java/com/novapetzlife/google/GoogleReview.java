package com.novapetzlife.google;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "google_reviews")
public class GoogleReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_name", nullable = false, length = 150)
    private String authorName;

    @Column(name = "author_photo_url", length = 500)
    private String authorPhotoUrl;

    @Column(nullable = false)
    private Integer rating;

    @Column(name = "relative_time_description", length = 100)
    private String relativeTimeDescription;

    @Column(name = "review_text", nullable = false, columnDefinition = "TEXT")
    private String reviewText;

    @Column(name = "google_review_id", unique = true, length = 150)
    private String googleReviewId;

    @Column(name = "review_timestamp")
    private Instant reviewTimestamp = Instant.now();

    @Column(name = "is_featured", nullable = false)
    private Boolean isFeatured = true;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    public GoogleReview() {}

    public GoogleReview(
            String authorName,
            String authorPhotoUrl,
            Integer rating,
            String relativeTimeDescription,
            String reviewText,
            String googleReviewId,
            Boolean isFeatured) {
        this.authorName = authorName;
        this.authorPhotoUrl = authorPhotoUrl;
        this.rating = rating;
        this.relativeTimeDescription = relativeTimeDescription;
        this.reviewText = reviewText;
        this.googleReviewId = googleReviewId;
        this.isFeatured = isFeatured != null ? isFeatured : true;
        this.reviewTimestamp = Instant.now();
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorPhotoUrl() {
        return authorPhotoUrl;
    }

    public void setAuthorPhotoUrl(String authorPhotoUrl) {
        this.authorPhotoUrl = authorPhotoUrl;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getRelativeTimeDescription() {
        return relativeTimeDescription;
    }

    public void setRelativeTimeDescription(String relativeTimeDescription) {
        this.relativeTimeDescription = relativeTimeDescription;
    }

    public String getReviewText() {
        return reviewText;
    }

    public void setReviewText(String reviewText) {
        this.reviewText = reviewText;
    }

    public String getGoogleReviewId() {
        return googleReviewId;
    }

    public void setGoogleReviewId(String googleReviewId) {
        this.googleReviewId = googleReviewId;
    }

    public Instant getReviewTimestamp() {
        return reviewTimestamp;
    }

    public void setReviewTimestamp(Instant reviewTimestamp) {
        this.reviewTimestamp = reviewTimestamp;
    }

    public Boolean getIsFeatured() {
        return isFeatured;
    }

    public void setIsFeatured(Boolean featured) {
        isFeatured = featured;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
