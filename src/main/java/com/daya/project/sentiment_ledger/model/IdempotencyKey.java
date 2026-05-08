package com.daya.project.sentiment_ledger.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "idempotency_keys")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class IdempotencyKey {

    @Id
    private String id;

    // Client-provided unique key (UUID)
    private String clientProvidedKey;

    // Invoice ID that was created/processed
    private String invoiceId;

    // Status of the idempotency record
    private String responseStatus; // PENDING, COMPLETED, FAILED

    // The response body to return on retry
    private String responseBody;

    // When this request was first received
    private Instant createdAt;

    // When this idempotency record expires (24 hours default)
    private Instant expiresAt;

    // Convenience constructor
    public IdempotencyKey(String clientProvidedKey, String invoiceId, String responseBody) {
        this.id = java.util.UUID.randomUUID().toString();
        this.clientProvidedKey = clientProvidedKey;
        this.invoiceId = invoiceId;
        this.responseStatus = "COMPLETED";
        this.responseBody = responseBody;
        this.createdAt = Instant.now();
        this.expiresAt = Instant.now().plus(java.time.Duration.ofHours(24));
    }

    /**
     * Check if this idempotency record is still valid (not expired)
     */
    public boolean isValid() {
        return Instant.now().isBefore(this.expiresAt);
    }
}