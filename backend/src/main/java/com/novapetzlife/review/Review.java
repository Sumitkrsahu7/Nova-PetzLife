package com.novapetzlife.review;

import com.novapetzlife.customer.Customer;
import com.novapetzlife.product.Product;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private Integer rating;

    @Column(length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String comment;

    @Column(name = "is_verified_purchase", nullable = false)
    private Boolean isVerifiedPurchase = false;

    @Column(nullable = false, length = 30)
    private String status = "APPROVED"; // PENDING, APPROVED, REJECTED

    @Column(name = "admin_reply", columnDefinition = "TEXT")
    private String adminReply;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ReviewImage> images = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public Review() {}

    public Review(Product product, Customer customer, Integer rating, String title, String comment, Boolean isVerifiedPurchase, String status) {
        this.product = product;
        this.customer = customer;
        this.rating = rating;
        this.title = title;
        this.comment = comment;
        this.isVerifiedPurchase = isVerifiedPurchase != null ? isVerifiedPurchase : false;
        this.status = status != null ? status : "APPROVED";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void addImage(String imageUrl) {
        ReviewImage img = new ReviewImage(this, imageUrl);
        images.add(img);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Boolean getIsVerifiedPurchase() {
        return isVerifiedPurchase;
    }

    public void setIsVerifiedPurchase(Boolean verifiedPurchase) {
        isVerifiedPurchase = verifiedPurchase;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAdminReply() {
        return adminReply;
    }

    public void setAdminReply(String adminReply) {
        this.adminReply = adminReply;
    }

    public List<ReviewImage> getImages() {
        return images;
    }

    public void setImages(List<ReviewImage> images) {
        this.images = images;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
