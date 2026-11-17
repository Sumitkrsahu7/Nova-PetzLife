package com.novapetzlife.order;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductVariant;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonBackReference
    private Order order;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "brand_name", nullable = false, length = 150)
    private String brandName;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "hsn_code", length = 50)
    private String hsnCode;

    @Column(name = "return_policy_snapshot", nullable = false, columnDefinition = "TEXT")
    private String returnPolicySnapshot; // Immutable owner return snapshot at purchase

    @Column(name = "refund_policy_snapshot", nullable = false, columnDefinition = "TEXT")
    private String refundPolicySnapshot; // Immutable owner refund snapshot at purchase

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    public OrderItem() {}

    public OrderItem(
            Order order,
            Product product,
            ProductVariant variant,
            String productName,
            String brandName,
            String sku,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            String hsnCode,
            String returnPolicySnapshot,
            String refundPolicySnapshot) {
        this.order = order;
        this.product = product;
        this.variant = variant;
        this.productName = productName;
        this.brandName = brandName;
        this.sku = sku;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
        this.taxRate = taxRate != null ? taxRate : BigDecimal.ZERO;
        this.taxAmount = taxAmount != null ? taxAmount : BigDecimal.ZERO;
        this.hsnCode = hsnCode;
        this.returnPolicySnapshot = returnPolicySnapshot;
        this.refundPolicySnapshot = refundPolicySnapshot;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
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

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public String getHsnCode() {
        return hsnCode;
    }

    public void setHsnCode(String hsnCode) {
        this.hsnCode = hsnCode;
    }

    public String getReturnPolicySnapshot() {
        return returnPolicySnapshot;
    }

    public void setReturnPolicySnapshot(String returnPolicySnapshot) {
        this.returnPolicySnapshot = returnPolicySnapshot;
    }

    public String getRefundPolicySnapshot() {
        return refundPolicySnapshot;
    }

    public void setRefundPolicySnapshot(String refundPolicySnapshot) {
        this.refundPolicySnapshot = refundPolicySnapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
