package com.novapetzlife.delivery;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "shop_location")
public class ShopLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_name", nullable = false, length = 150)
    private String shopName = "NOVA PETZLIFE";

    @Column(name = "address_line", nullable = false, length = 255)
    private String addressLine;

    @Column(nullable = false, length = 100)
    private String city = "Thiruvananthapuram";

    @Column(nullable = false, length = 100)
    private String state = "Kerala";

    @Column(nullable = false, length = 100)
    private String country = "India";

    @Column(name = "pin_code", nullable = false, length = 20)
    private String pinCode = "695003";

    @Column(name = "google_place_id", length = 150)
    private String googlePlaceId;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "delivery_radius_km", nullable = false, precision = 5, scale = 2)
    private BigDecimal deliveryRadiusKm = new BigDecimal("20.00");

    @Column(name = "directions_url", length = 500)
    private String directionsUrl;

    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = false;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public ShopLocation() {}

    public ShopLocation(
            String shopName,
            String addressLine,
            String city,
            String state,
            String country,
            String pinCode,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal deliveryRadiusKm,
            Boolean isVerified) {
        this.shopName = shopName;
        this.addressLine = addressLine;
        this.city = city;
        this.state = state;
        this.country = country;
        this.pinCode = pinCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.deliveryRadiusKm = deliveryRadiusKm != null ? deliveryRadiusKm : new BigDecimal("20.00");
        this.isVerified = isVerified != null ? isVerified : false;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getPinCode() {
        return pinCode;
    }

    public void setPinCode(String pinCode) {
        this.pinCode = pinCode;
    }

    public String getGooglePlaceId() {
        return googlePlaceId;
    }

    public void setGooglePlaceId(String googlePlaceId) {
        this.googlePlaceId = googlePlaceId;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getDeliveryRadiusKm() {
        return deliveryRadiusKm;
    }

    public void setDeliveryRadiusKm(BigDecimal deliveryRadiusKm) {
        this.deliveryRadiusKm = deliveryRadiusKm;
    }

    public String getDirectionsUrl() {
        return directionsUrl;
    }

    public void setDirectionsUrl(String directionsUrl) {
        this.directionsUrl = directionsUrl;
    }

    public Boolean getIsVerified() {
        return isVerified;
    }

    public void setIsVerified(Boolean isVerified) {
        this.isVerified = isVerified;
        if (Boolean.TRUE.equals(isVerified)) {
            this.verifiedAt = Instant.now();
        } else {
            this.verifiedAt = null;
        }
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
