package com.novapetzlife.tax;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "tax_configurations")
public class TaxConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tax_name", nullable = false, length = 100)
    private String taxName;

    @Column(name = "hsn_code", nullable = false, length = 50)
    private String hsnCode;

    @Column(name = "cgst_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal cgstRate = BigDecimal.ZERO;

    @Column(name = "sgst_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal sgstRate = BigDecimal.ZERO;

    @Column(name = "igst_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal igstRate = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    public TaxConfiguration() {}

    public TaxConfiguration(String taxName, String hsnCode, BigDecimal cgstRate, BigDecimal sgstRate, BigDecimal igstRate) {
        this.taxName = taxName;
        this.hsnCode = hsnCode;
        this.cgstRate = cgstRate != null ? cgstRate : BigDecimal.ZERO;
        this.sgstRate = sgstRate != null ? sgstRate : BigDecimal.ZERO;
        this.igstRate = igstRate != null ? igstRate : BigDecimal.ZERO;
        this.isActive = true;
        this.createdAt = Instant.now();
    }

    public BigDecimal getTotalGstRate() {
        return cgstRate.add(sgstRate);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaxName() {
        return taxName;
    }

    public void setTaxName(String taxName) {
        this.taxName = taxName;
    }

    public String getHsnCode() {
        return hsnCode;
    }

    public void setHsnCode(String hsnCode) {
        this.hsnCode = hsnCode;
    }

    public BigDecimal getCgstRate() {
        return cgstRate;
    }

    public void setCgstRate(BigDecimal cgstRate) {
        this.cgstRate = cgstRate;
    }

    public BigDecimal getSgstRate() {
        return sgstRate;
    }

    public void setSgstRate(BigDecimal sgstRate) {
        this.sgstRate = sgstRate;
    }

    public BigDecimal getIgstRate() {
        return igstRate;
    }

    public void setIgstRate(BigDecimal igstRate) {
        this.igstRate = igstRate;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
