package com.novapetzlife.returns;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "return_inspections")
public class ReturnInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_request_id", nullable = false)
    @JsonBackReference
    private ReturnRequest returnRequest;

    @Column(name = "inspected_by", nullable = false, length = 100)
    private String inspectedBy;

    @Column(nullable = false, length = 50)
    private String outcome; // APPROVED, REJECTED, PARTIALLY_APPROVED

    @Column(name = "condition_notes", nullable = false, columnDefinition = "TEXT")
    private String conditionNotes;

    @Column(name = "inspected_at")
    private Instant inspectedAt = Instant.now();

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    public ReturnInspection() {}

    public ReturnInspection(ReturnRequest returnRequest, String inspectedBy, String outcome, String conditionNotes) {
        this.returnRequest = returnRequest;
        this.inspectedBy = inspectedBy;
        this.outcome = outcome;
        this.conditionNotes = conditionNotes;
        this.inspectedAt = Instant.now();
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ReturnRequest getReturnRequest() {
        return returnRequest;
    }

    public void setReturnRequest(ReturnRequest returnRequest) {
        this.returnRequest = returnRequest;
    }

    public String getInspectedBy() {
        return inspectedBy;
    }

    public void setInspectedBy(String inspectedBy) {
        this.inspectedBy = inspectedBy;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getConditionNotes() {
        return conditionNotes;
    }

    public void setConditionNotes(String conditionNotes) {
        this.conditionNotes = conditionNotes;
    }

    public Instant getInspectedAt() {
        return inspectedAt;
    }

    public void setInspectedAt(Instant inspectedAt) {
        this.inspectedAt = inspectedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
