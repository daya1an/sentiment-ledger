package com.daya.project.sentiment_ledger.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published to Kafka when invoice decision is made
 * Signals payment service to execute payment if approved
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentEvent {

    private String invoiceId;      // ID of invoice to pay
    private BigDecimal amount;     // Amount to pay
    private String decision;       // APPROVED, REJECTED, MANUAL_REVIEW
    private Instant createdAt;     // When event was created

    /**
     * Convenience constructor without timestamp
     */
    public PaymentEvent(String invoiceId, BigDecimal amount, String decision) {
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.decision = decision;
        this.createdAt = Instant.now();
    }

    /**
     * Check if payment should be executed
     */
    public boolean shouldExecutePayment() {
        return "APPROVED".equalsIgnoreCase(this.decision);
    }
}