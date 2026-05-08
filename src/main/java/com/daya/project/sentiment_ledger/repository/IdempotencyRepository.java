package com.daya.project.sentiment_ledger.repository;

import com.daya.project.sentiment_ledger.model.IdempotencyKey;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface IdempotencyRepository extends MongoRepository<IdempotencyKey, String> {

    /**
     * Find idempotency record by client-provided key
     * @param clientProvidedKey UUID provided by client
     * @return Optional containing IdempotencyKey if found
     */
    Optional<IdempotencyKey> findByClientProvidedKey(String clientProvidedKey);

    /**
     * Find idempotency records by invoice ID
     * Useful for auditing all requests that created this invoice
     * @param invoiceId ID of the invoice
     * @return List of all idempotency records for this invoice
     */
    List<IdempotencyKey> findByInvoiceId(String invoiceId);

    /**
     * Find idempotency records by status
     * Useful for monitoring failed or pending requests
     * @param status PENDING, COMPLETED, or FAILED
     * @return List of idempotency records with given status
     */
    List<IdempotencyKey> findByResponseStatus(String status);

    /**
     * Find expired idempotency records for cleanup
     * @param beforeTime Instant to compare against
     * @return List of expired records
     */
    @Query("{ 'expiresAt': { $lt: ?0 } }")
    List<IdempotencyKey> findExpiredRecords(Instant beforeTime);

    /**
     * Delete expired idempotency records to save storage
     * Called periodically by background job
     * @param beforeTime Instant to compare against
     * @return Number of records deleted
     */
    long deleteByExpiresAtBefore(Instant beforeTime);
}