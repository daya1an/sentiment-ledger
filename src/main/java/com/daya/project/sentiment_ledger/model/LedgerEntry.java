package com.daya.project.sentiment_ledger.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "ledger_entries")
public class LedgerEntry {

    @Id
    private String id;
    private String invoiceId;
    private String actionTaken; // "CREATED", "APPROVED", "PAID"

    // This is crucial for Phase 6: We will store the AI's exact thought process here
    private String aiReasoningContext;
    private Instant timestamp;

    public LedgerEntry() {}

    public LedgerEntry(String invoiceId, String actionTaken, String aiReasoningContext) {
        this.invoiceId = invoiceId;
        this.actionTaken = actionTaken;
        this.aiReasoningContext = aiReasoningContext;
        this.timestamp = Instant.now();
    }

    // Getters and Setters omitted for brevity
    public String getId() { return id; }
    public String getInvoiceId() { return invoiceId; }
    public String getActionTaken() { return actionTaken; }
    public String getAiReasoningContext() { return aiReasoningContext; }
    public Instant getTimestamp() { return timestamp; }
}
