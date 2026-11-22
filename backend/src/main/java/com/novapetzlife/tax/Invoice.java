package com.novapetzlife.tax;

import com.novapetzlife.order.Order;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 100)
    private String invoiceNumber;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate = LocalDate.now();

    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    @Column(name = "customer_gstin", length = 50)
    private String customerGstin;

    @Column(name = "total_taxable_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalTaxableValue;

    @Column(name = "total_cgst", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCgst = BigDecimal.ZERO;

    @Column(name = "total_sgst", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalSgst = BigDecimal.ZERO;

    @Column(name = "total_igst", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalIgst = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType = "PROVISIONAL_RECEIPT";

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "pdf_url", length = 500)
    private String pdfUrl;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    public Invoice() {}

    public Invoice(
            Order order,
            String invoiceNumber,
            LocalDate invoiceDate,
            String customerName,
            String customerGstin,
            BigDecimal totalTaxableValue,
            BigDecimal totalCgst,
            BigDecimal totalSgst,
            BigDecimal totalIgst,
            BigDecimal totalAmount) {
        this.order = order;
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate != null ? invoiceDate : LocalDate.now();
        this.customerName = customerName;
        this.customerGstin = customerGstin;
        this.totalTaxableValue = totalTaxableValue;
        this.totalCgst = totalCgst != null ? totalCgst : BigDecimal.ZERO;
        this.totalSgst = totalSgst != null ? totalSgst : BigDecimal.ZERO;
        this.totalIgst = totalIgst != null ? totalIgst : BigDecimal.ZERO;
        this.totalAmount = totalAmount;
        this.documentType = "PROVISIONAL_RECEIPT";
        this.discountAmount = BigDecimal.ZERO;
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

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(LocalDate invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerGstin() {
        return customerGstin;
    }

    public void setCustomerGstin(String customerGstin) {
        this.customerGstin = customerGstin;
    }

    public BigDecimal getTotalTaxableValue() {
        return totalTaxableValue;
    }

    public void setTotalTaxableValue(BigDecimal totalTaxableValue) {
        this.totalTaxableValue = totalTaxableValue;
    }

    public BigDecimal getTotalCgst() {
        return totalCgst;
    }

    public void setTotalCgst(BigDecimal totalCgst) {
        this.totalCgst = totalCgst;
    }

    public BigDecimal getTotalSgst() {
        return totalSgst;
    }

    public void setTotalSgst(BigDecimal totalSgst) {
        this.totalSgst = totalSgst;
    }

    public BigDecimal getTotalIgst() {
        return totalIgst;
    }

    public void setTotalIgst(BigDecimal totalIgst) {
        this.totalIgst = totalIgst;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getPdfUrl() {
        return pdfUrl;
    }

    public void setPdfUrl(String pdfUrl) {
        this.pdfUrl = pdfUrl;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType != null ? documentType : "PROVISIONAL_RECEIPT";
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
