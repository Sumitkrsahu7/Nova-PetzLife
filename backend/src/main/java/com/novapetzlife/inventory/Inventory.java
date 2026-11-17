package com.novapetzlife.inventory;

import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductVariant;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(name = "current_stock", nullable = false)
    private Integer currentStock = 0;

    @Column(name = "available_stock", nullable = false)
    private Integer availableStock = 0;

    @Column(name = "reserved_stock", nullable = false)
    private Integer reservedStock = 0;

    @Column(name = "min_stock_threshold", nullable = false)
    private Integer minStockThreshold = 5;

    @Column(name = "max_stock_threshold")
    private Integer maxStockThreshold;

    @Column(name = "damaged_stock", nullable = false)
    private Integer damagedStock = 0;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public Inventory() {}

    public Inventory(Product product, ProductVariant variant, int initialStock, int minThreshold) {
        this.product = product;
        this.variant = variant;
        this.currentStock = initialStock;
        this.availableStock = initialStock;
        this.reservedStock = 0;
        this.minStockThreshold = minThreshold;
        this.damagedStock = 0;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void reserve(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to reserve must be greater than zero");
        }
        if (this.availableStock < quantity) {
            throw new IllegalStateException("Insufficient available stock. Requested: " + quantity + ", Available: " + this.availableStock);
        }
        this.availableStock -= quantity;
        this.reservedStock += quantity;
    }

    public void releaseReserved(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to release must be greater than zero");
        }
        int releaseQty = Math.min(this.reservedStock, quantity);
        this.reservedStock -= releaseQty;
        this.availableStock += releaseQty;
    }

    public void deductConfirmed(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to deduct must be greater than zero");
        }
        if (this.reservedStock >= quantity) {
            this.reservedStock -= quantity;
        } else {
            int remaining = quantity - this.reservedStock;
            this.reservedStock = 0;
            this.availableStock = Math.max(0, this.availableStock - remaining);
        }
        this.currentStock = Math.max(0, this.currentStock - quantity);
    }

    public void restock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Restock quantity must be positive");
        }
        this.currentStock += quantity;
        this.availableStock += quantity;
    }

    public boolean isLowStock() {
        return this.availableStock <= this.minStockThreshold;
    }

    public Integer getLowStockThreshold() {
        return this.minStockThreshold;
    }

    public boolean isOutOfStock() {
        return this.availableStock <= 0;
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

    public ProductVariant getVariant() {
        return variant;
    }

    public void setVariant(ProductVariant variant) {
        this.variant = variant;
    }

    public Integer getCurrentStock() {
        return currentStock;
    }

    public void setCurrentStock(Integer currentStock) {
        this.currentStock = currentStock;
    }

    public Integer getAvailableStock() {
        return availableStock;
    }

    public void setAvailableStock(Integer availableStock) {
        this.availableStock = availableStock;
    }

    public Integer getReservedStock() {
        return reservedStock;
    }

    public void setReservedStock(Integer reservedStock) {
        this.reservedStock = reservedStock;
    }

    public Integer getMinStockThreshold() {
        return minStockThreshold;
    }

    public void setMinStockThreshold(Integer minStockThreshold) {
        this.minStockThreshold = minStockThreshold;
    }

    public Integer getMaxStockThreshold() {
        return maxStockThreshold;
    }

    public void setMaxStockThreshold(Integer maxStockThreshold) {
        this.maxStockThreshold = maxStockThreshold;
    }

    public Integer getDamagedStock() {
        return damagedStock;
    }

    public void setDamagedStock(Integer damagedStock) {
        this.damagedStock = damagedStock;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
