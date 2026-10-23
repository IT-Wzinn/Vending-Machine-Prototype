package com.vendingmachine.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ReconciliationDecisionRequestDto {

    public enum ReconciliationDecision {
        MANUAL_DISPENSE_APPROVED,
        REFUND_PENDING,
        REFUND_CONFIRMED,
        INVESTIGATION_NOTE
    }

    @NotNull(message = "Quyết định đối soát (decision) là bắt buộc.")
    private ReconciliationDecision decision;

    @NotBlank(message = "Lý do đối soát (reason) là bắt buộc.")
    private String reason;

    private String confirmationReference;

    public ReconciliationDecisionRequestDto() {
    }

    public ReconciliationDecisionRequestDto(ReconciliationDecision decision, String reason, String confirmationReference) {
        this.decision = decision;
        this.reason = reason;
        this.confirmationReference = confirmationReference;
    }

    public ReconciliationDecision getDecision() { return decision; }
    public void setDecision(ReconciliationDecision decision) { this.decision = decision; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getConfirmationReference() { return confirmationReference; }
    public void setConfirmationReference(String confirmationReference) { this.confirmationReference = confirmationReference; }
}
