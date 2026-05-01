package com.daya.project.sentiment_ledger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    public String executePayout(String invoiceId, BigDecimal amount) {
        log.info("Initiating Razorpay transfer for Invoice: {} | Amount: ₹{}", invoiceId, amount);

        // Simulate network delay
        try { Thread.sleep(800); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        String mockTransactionId = "txn_" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Transfer successful! Transaction ID: {}", mockTransactionId);

        return mockTransactionId;
    }
}