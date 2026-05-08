package com.daya.project.sentiment_ledger.service.payment;

import com.daya.project.sentiment_ledger.exception.PaymentServiceException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;

@Service
@Slf4j
public class PaymentService {

    private static final int MAX_RETRIES = 3;
    private static final long BACKOFF_MULTIPLIER = 2;

    @Retryable(
            retryFor = {PaymentServiceException.class},
            maxAttempts = MAX_RETRIES,
            backoff = @Backoff(delay = 1000, multiplier = BACKOFF_MULTIPLIER)
    )
    public String executePayout(String invoiceId, BigDecimal amount) {
        log.info("💳 Initiating payment for Invoice: {} | Amount: ₹{}", invoiceId, amount);

        try {
            // Simulate payment with potential failure
            if (new Random().nextDouble() < 0.05) { // 5% failure rate
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

    @Recover
    public String recoverPayment(PaymentServiceException ex, String invoiceId, BigDecimal amount) {
        log.error("❌ Payment failed after {} retries for invoice {}", MAX_RETRIES, invoiceId);
        // Create a fallback transaction record
        return "txn_fallback_" + invoiceId;
    }
}