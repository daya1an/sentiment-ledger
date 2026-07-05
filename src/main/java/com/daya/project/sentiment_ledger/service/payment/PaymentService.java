package com.daya.project.sentiment_ledger.service.payment;

import com.daya.project.sentiment_ledger.exception.PaymentServiceException;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Transfer;
import com.stripe.param.TransferCreateParams;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
public class PaymentService {

    private final String stripeSecretApiKey;

    public PaymentService(@Value("${stripe.secret.api.key}") String stripeSecretApiKey) {
        this.stripeSecretApiKey = stripeSecretApiKey;
    }

    @CircuitBreaker(name = "payment-service", fallbackMethod = "fallbackPayment")
    @Retry(name = "payment-retry", fallbackMethod = "fallbackPayment")
    public String executePayout(String invoiceId, BigDecimal amount, String vendorName, String vendorStripeConnectId) {

        log.info("💳 Executing Stripe Transfer for Invoice: {} | Vendor: {} | Amount: ${} | ConnectId: {}",
                invoiceId, vendorName, amount, vendorStripeConnectId);

        // Validate vendor Stripe Connect ID
        if (vendorStripeConnectId == null || vendorStripeConnectId.trim().isEmpty()) {
            throw new PaymentServiceException(invoiceId, "Vendor Stripe Connect ID is missing. Cannot process payment.");
        }

        Stripe.apiKey = stripeSecretApiKey;

        try {
            long amountInCents = amount.multiply(new BigDecimal(100)).longValue();

            // Create Transfer to vendor's Stripe Connect Account
            // This DEBITS company's account and CREDITS vendor's account
            TransferCreateParams params = TransferCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency("usd")
                    .setDestination(vendorStripeConnectId)  // Vendor's Stripe Connect Account ID
                    .setDescription("Automated Ledger Payment for Invoice: " + invoiceId)
                    .putMetadata("invoiceId", invoiceId)
                    .putMetadata("vendorName", vendorName)
                    .build();

            // Execute the transfer
            Transfer transfer = Transfer.create(params);

            // Stripe Transfer is created successfully if we reach here
            // Transfer object contains the transfer details
            if (transfer != null && transfer.getId() != null) {
                log.info("✅ Transfer SUCCESS! Vendor: {} | Amount: ${} | Stripe Transfer ID: {}",
                        vendorName, amount, transfer.getId());
                return transfer.getId(); // Returns 't_...' as the transfer ID
            } else {
                throw new PaymentServiceException(invoiceId, "Transfer creation failed - no transfer ID returned");
            }

        } catch (StripeException e) {
            log.error("❌ Stripe Transfer failed for vendor {}: {}", vendorName, e.getMessage(), e);
            throw new PaymentServiceException(invoiceId, e.getMessage());
        }
    }

    public String fallbackPayment(String invoiceId, BigDecimal amount, String vendorName, String vendorStripeConnectId, Exception ex) {
        log.warn("⚠️ FALLBACK triggered for invoice: {}. Marking as PENDING_RETRY.", invoiceId);
        return "txn_pending_" + invoiceId;
    }
}