package com.novapetzlife.analytics.dto;

public class LowStockAlertDto {

    private Long productId;
    private String productName;
    private String sku;
    private String categoryName;
    private int currentStock;
    private int lowStockThreshold;
    private int suggestedReorder;

    public LowStockAlertDto() {}

    public LowStockAlertDto(Long productId, String productName, String sku, String categoryName, int currentStock, int lowStockThreshold, int suggestedReorder) {
        this.productId = productId;
        this.productName = productName;
        this.sku = sku;
        this.categoryName = categoryName;
        this.currentStock = currentStock;
        this.lowStockThreshold = lowStockThreshold;
        this.suggestedReorder = suggestedReorder;
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public int getCurrentStock() { return currentStock; }
    public void setCurrentStock(int currentStock) { this.currentStock = currentStock; }
    public int getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(int lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }
    public int getSuggestedReorder() { return suggestedReorder; }
    public void setSuggestedReorder(int suggestedReorder) { this.suggestedReorder = suggestedReorder; }
}
