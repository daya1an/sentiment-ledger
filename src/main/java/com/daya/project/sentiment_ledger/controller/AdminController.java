package com.daya.project.sentiment_ledger.controller;

import com.daya.project.sentiment_ledger.service.payment.PaymentCompensationService;
import com.daya.project.sentiment_ledger.service.payment.StripeValidationService;
import com.daya.project.sentiment_ledger.service.policy.PolicyRetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin")
@Slf4j
public class AdminController {

    private final PolicyRetrievalService policyRetrievalService;
    private final PaymentCompensationService paymentCompensationService;
    private final StripeValidationService validationService;


    public AdminController(PolicyRetrievalService policyRetrievalService,
                           PaymentCompensationService paymentCompensationService,
                           StripeValidationService validationService) {
        this.policyRetrievalService = policyRetrievalService;
        this.paymentCompensationService = paymentCompensationService;
        this.validationService = validationService;
    }

    @PostMapping("/refresh-policies-cache")
    public ResponseEntity<String> refreshPoliciesCache() {
        policyRetrievalService.refreshPoliciesCache();
        log.info("✅ Policies cache refreshed");
        return ResponseEntity.ok("Policies cache refreshed successfully");
    }

    @GetMapping("/retry-pending-payments")
    public ResponseEntity<String> retryPendingPayments() {
        paymentCompensationService.retryPendingPayments();
        return ResponseEntity.ok("Pending payments retry initiated");
    }

    @GetMapping("/validate/{stripeId}")
    public ResponseEntity<Map<String, Object>> validateStripeId(@PathVariable String stripeId) {
        boolean isValid = validationService.isStripeIdValid(stripeId);

        if (isValid) {
            return ResponseEntity.ok(Map.of(
                    "stripeId", stripeId,
                    "valid", true,
                    "message", "Transaction ID exists and is valid in Stripe."
            ));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "stripeId", stripeId,
                    "valid", false,
                    "message", "Transaction ID not found in Stripe or is invalid."
            ));
        }
    }
}
