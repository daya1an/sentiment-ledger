package com.daya.project.sentiment_ledger.service.payment;

import com.stripe.Stripe;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Transfer;
import com.stripe.model.checkout.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class StripeValidationService {

    private final String stripeSecretApiKey;

    public StripeValidationService(@Value("${stripe.secret.api.key}") String stripeSecretApiKey) {
        this.stripeSecretApiKey = stripeSecretApiKey;
    }

    /**
     * Queries Stripe to verify if the given ID actually exists in your Stripe account.
     */
    public boolean isStripeIdValid(String stripeId) {
        if (stripeId == null || stripeId.isBlank()) {
            return false;
        }

        Stripe.apiKey = stripeSecretApiKey;

        try {
            // Route the retrieval based on the standard Stripe prefixes
            if (stripeId.startsWith("pi_")) {
                PaymentIntent.retrieve(stripeId);
                return true;
            } else if (stripeId.startsWith("tr_")) {
                Transfer.retrieve(stripeId);
                return true;
            } else if (stripeId.startsWith("cs_")) {
                Session.retrieve(stripeId);
                return true;
            } else {
                log.warn("⚠️ Unrecognized Stripe ID prefix for validation: {}", stripeId);
                return false;
            }

        } catch (InvalidRequestException e) {
            // A 404 status code explicitly means the object does not exist in Stripe
            if (e.getStatusCode() == 404) {
                log.info("🔍 Stripe ID {} does not exist.", stripeId);
                return false;
            }
            log.error("❌ Invalid request sent to Stripe: {}", e.getMessage());
            return false;

        } catch (StripeException e) {
            log.error("❌ Stripe API error during validation: {}", e.getMessage(), e);
            return false;
        }
    }
}