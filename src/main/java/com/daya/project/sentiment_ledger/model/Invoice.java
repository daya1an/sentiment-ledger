package com.daya.project.sentiment_ledger.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "invoices")
public class Invoice {

    @Id
    private String id;
    private String vendorName;
    private BigDecimal amount;
    private String category; // e.g., "SOFTWARE", "HARDWARE", "TRAVEL"
    private Status status;
    private Instant createdAt;

    public enum Status {
        PENDING, AI_APPROVED, REJECTED, PAID, DUPLICATE_FLAGGED
    }

    // Constructors
    public Invoice() {
        this.createdAt = Instant.now();
        this.status = Status.PENDING;
    }

    public Invoice(String vendorName, BigDecimal amount, String category) {
        this();
        this.vendorName = vendorName;
        this.amount = amount;
        this.category = category;
    }

    // Getters and Setters omitted for brevity (Generate them in your IDE)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
