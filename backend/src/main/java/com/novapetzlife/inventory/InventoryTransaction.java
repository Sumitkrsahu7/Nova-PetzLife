package com.novapetzlife.inventory;

import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductVariant;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "inventory_transactions")
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(name = "transaction_type", nullable = false, length = 50)
    private String transactionType; // RESTOCK, RESERVED, DEDUCTED, RELEASED, DAMAGED, ADJUSTMENT

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "reference_type", length = 50)
    private String referenceType; // ORDER, MANUAL, RETURN

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(length = 255)
    private String notes;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    public InventoryTransaction() {}

    public InventoryTransaction(Product product, ProductVariant variant, String transactionType, Integer quantity, String referenceType, String referenceId, String notes) {
        this.product = product;
        this.variant = variant;
        this.transactionType = transactionType;
        this.quantity = quantity;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.notes = notes;
        this.createdAt = Instant.now();
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

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
