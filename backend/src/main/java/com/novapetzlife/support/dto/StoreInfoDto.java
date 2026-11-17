package com.novapetzlife.support.dto;

import java.util.List;

public class StoreInfoDto {

    private String shopName;
    private String listingName;
    private String address;
    private String landmark;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private double latitude;
    private double longitude;
    private double deliveryRadiusKm;
    private String phone;
    private String whatsapp;
    private String email;
    private String openingHours;
    private List<String> availableServices;

    public StoreInfoDto() {}

    public StoreInfoDto(
            String shopName,
            String listingName,
            String address,
            String landmark,
            String city,
            String state,
            String country,
            String postalCode,
            double latitude,
            double longitude,
            double deliveryRadiusKm,
            String phone,
            String whatsapp,
            String email,
            String openingHours,
            List<String> availableServices) {
        this.shopName = shopName;
        this.listingName = listingName;
        this.address = address;
        this.landmark = landmark;
        this.city = city;
        this.state = state;
        this.country = country;
        this.postalCode = postalCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.deliveryRadiusKm = deliveryRadiusKm;
        this.phone = phone;
        this.whatsapp = whatsapp;
        this.email = email;
        this.openingHours = openingHours;
        this.availableServices = availableServices;
    }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }
    public String getListingName() { return listingName; }
    public void setListingName(String listingName) { this.listingName = listingName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getLandmark() { return landmark; }
    public void setLandmark(String landmark) { this.landmark = landmark; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public double getDeliveryRadiusKm() { return deliveryRadiusKm; }
    public void setDeliveryRadiusKm(double deliveryRadiusKm) { this.deliveryRadiusKm = deliveryRadiusKm; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getWhatsapp() { return whatsapp; }
    public void setWhatsapp(String whatsapp) { this.whatsapp = whatsapp; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getOpeningHours() { return openingHours; }
    public void setOpeningHours(String openingHours) { this.openingHours = openingHours; }
    public List<String> getAvailableServices() { return availableServices; }
    public void setAvailableServices(List<String> availableServices) { this.availableServices = availableServices; }
}
