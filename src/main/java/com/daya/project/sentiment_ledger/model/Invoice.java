package com.daya.project.sentiment_ledger.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "invoices")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Invoice {

    @Id
    private String id;
    private String vendorName;
    private BigDecimal amount;
    private String category;
    private Status status;
    private Instant createdAt;
    private double confidenceScore; // NEW: AI confidence in decision
    private String approvalLevel; // NEW: CFO, DIRECTOR, MANAGER, NONE
    private String vendorStripeConnectId; // Vendor's Stripe Connect Account ID for payouts

    public enum Status {
        PENDING, AI_APPROVED, REJECTED, PAID, DUPLICATE_FLAGGED
    }
}
