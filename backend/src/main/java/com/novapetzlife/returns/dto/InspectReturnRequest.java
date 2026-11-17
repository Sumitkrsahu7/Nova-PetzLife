package com.novapetzlife.returns.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public class InspectReturnRequest {

    @NotBlank(message = "Inspection outcome is required (APPROVED, REJECTED, PARTIALLY_APPROVED)")
    private String outcome;

    @NotBlank(message = "Condition notes are required")
    private String conditionNotes;

    private boolean restockInventory = true;
    private boolean approveRefund = true;
    private BigDecimal refundAmount;
    private String adminNotes;

    public InspectReturnRequest() {}

    public InspectReturnRequest(String outcome, String conditionNotes, boolean restockInventory, boolean approveRefund, BigDecimal refundAmount, String adminNotes) {
        this.outcome = outcome;
        this.conditionNotes = conditionNotes;
        this.restockInventory = restockInventory;
        this.approveRefund = approveRefund;
        this.refundAmount = refundAmount;
        this.adminNotes = adminNotes;
    }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getConditionNotes() { return conditionNotes; }
    public void setConditionNotes(String conditionNotes) { this.conditionNotes = conditionNotes; }
    public boolean isRestockInventory() { return restockInventory; }
    public void setRestockInventory(boolean restockInventory) { this.restockInventory = restockInventory; }
    public boolean isApproveRefund() { return approveRefund; }
    public void setApproveRefund(boolean approveRefund) { this.approveRefund = approveRefund; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }
}
