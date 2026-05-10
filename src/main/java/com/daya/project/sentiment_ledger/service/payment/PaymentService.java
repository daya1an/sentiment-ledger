package com.daya.project.sentiment_ledger.service.payment;

import com.daya.project.sentiment_ledger.exception.PaymentServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;

@Service
@Slf4j
public class PaymentService {

    private static final int MAX_RETRIES = 3;
    private static final long BACKOFF_MULTIPLIER = 2;

    /**
     * CircuitBreaker: Prevents calling failed service repeatedly
     * - name: "payment-service" (matches config in application.properties)
     * - fallbackMethod: "fallbackPayment" (called when circuit is OPEN)
     *
     * Retry: Exponential backoff for transient failures
     * - name: "payment-retry" (matches config)
     * - fallbackMethod: Same fallback
     */
    @CircuitBreaker(
            name = "payment-service",
            fallbackMethod = "fallbackPayment"
    )
    @Retry(
            name = "payment-retry",
            fallbackMethod = "fallbackPayment"
    )
    public String executePayout(String invoiceId, BigDecimal amount) {
        log.info("💳 Initiating payment for Invoice: {} | Amount: ₹{}", invoiceId, amount);

        try {
            // Simulate payment with 5% failure rate
            if (new Random().nextDouble() < 0.05) {
                throw new Exception("Simulated payment gateway timeout");
            }

            Thread.sleep(800);

            String mockTransactionId = "txn_" + UUID.randomUUID().toString().substring(0, 8);
            log.info("✅ Transfer successful! Transaction ID: {}", mockTransactionId);
            return mockTransactionId;

        } catch (Exception e) {
            log.error("❌ Payment failed: {}", e.getMessage());
            throw new PaymentServiceException(invoiceId, e.getMessage());
        }
    }

    /**
     * PHASE 1: Fallback method
     * Called when:
     * 1. Circuit is OPEN (too many failures detected)
     * 2. All retries exhausted
     * 3. Deadline exceeded
     *
     * Creates pending transaction record for compensation job
     */
    public String fallbackPayment(String invoiceId, BigDecimal amount, Exception ex) {
        log.warn("⚠️ FALLBACK: Payment service unavailable for invoice: {}", invoiceId);
        log.warn("   Error: {}", ex.getMessage());
        log.warn("   Invoice will be marked PENDING for retry");

        // Create pending transaction ID
        // PaymentCompensationService will retry this periodically
        String pendingTxnId = "txn_pending_" + invoiceId;

        log.info("📋 Created pending transaction: {}", pendingTxnId);
        return pendingTxnId;
    }

    /**
     * PHASE 1: Recovery method
     * Checks if payment service health improved
     */
    public boolean isServiceHealthy() {
        try {
            // Simple health check
            Thread.sleep(100);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}