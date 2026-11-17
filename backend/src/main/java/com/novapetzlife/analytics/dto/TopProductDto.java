package com.novapetzlife.analytics.dto;

import java.math.BigDecimal;

public class TopProductDto {

    private Long productId;
    private String productName;
    private String brandName;
    private long totalQuantitySold;
    private BigDecimal totalRevenue;

    public TopProductDto() {}

    public TopProductDto(Long productId, String productName, String brandName, long totalQuantitySold, BigDecimal totalRevenue) {
        this.productId = productId;
        this.productName = productName;
        this.brandName = brandName;
        this.totalQuantitySold = totalQuantitySold;
        this.totalRevenue = totalRevenue;
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }
    public long getTotalQuantitySold() { return totalQuantitySold; }
    public void setTotalQuantitySold(long totalQuantitySold) { this.totalQuantitySold = totalQuantitySold; }
    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
}
