package com.daya.project.sentiment_ledger.service;

import com.daya.project.sentiment_ledger.config.KafkaTopicConfig;
import com.daya.project.sentiment_ledger.model.Invoice;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class InvoiceProcessor {

    private static final Logger log = LoggerFactory.getLogger(InvoiceProcessor.class);
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // Injecting both Redis and the JSON ObjectMapper
    public InvoiceProcessor(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // Notice we now consume a raw 'String' instead of an 'Invoice' object
    @KafkaListener(topics = KafkaTopicConfig.INVOICE_SUBMITTED_TOPIC, groupId = "ledger-processing-group")
    public void consumeInvoiceEvent(String invoiceJson) {
        try {
            // 1. Manually convert the JSON string into our Java object
            Invoice invoice = objectMapper.readValue(invoiceJson, Invoice.class);

            log.info("🔥 KAFKA EVENT RECEIVED for Invoice ID: {}", invoice.getId());

            // 2. Define the Idempotency Key
            String lockKey = "invoice:lock:" + invoice.getId();

            // 3. The Check & Lock (Atomic Operation)
            Boolean isNewInvoice = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", Duration.ofHours(24));

            if (Boolean.FALSE.equals(isNewInvoice)) {
                log.warn("⚠️ DUPLICATE DETECTED: Invoice ID {} is already locked. Dropping message.", invoice.getId());
                return;
            }

            log.info("✅ Lock acquired. Processing Invoice...");
            log.info("Vendor: {}", invoice.getVendorName());
            log.info("Amount: ₹{}", invoice.getAmount());

        } catch (Exception e) {
            // If the JSON is bad, we will finally see WHY!
            log.error("❌ Failed to process Kafka message: {}", e.getMessage());
        }
    }
}