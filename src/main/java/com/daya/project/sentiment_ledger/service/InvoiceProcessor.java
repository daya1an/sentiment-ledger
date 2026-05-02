package com.daya.project.sentiment_ledger.service;

import com.daya.project.sentiment_ledger.config.KafkaTopicConfig;
import com.daya.project.sentiment_ledger.model.Invoice;
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

    public InvoiceProcessor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @KafkaListener(topics = KafkaTopicConfig.INVOICE_SUBMITTED_TOPIC, groupId = "ledger-processing-group")
    public void consumeInvoiceEvent(Invoice invoice) {
        try {
            log.info("🔥 KAFKA EVENT RECEIVED for Invoice ID: {}", invoice.getId());

            String lockKey = "invoice:lock:" + invoice.getId();

            Boolean isNewInvoice = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", Duration.ofHours(24));

            if (Boolean.FALSE.equals(isNewInvoice)) {
                log.warn("⚠️ DUPLICATE DETECTED: Invoice ID {} is already locked. Dropping message.", invoice.getId());
                return;
            }

            log.info("✅ Lock acquired. Processing Invoice...");
            log.info("Vendor: {}", invoice.getVendorName());
            log.info("Amount: ₹{}", invoice.getAmount());

        } catch (Exception e) {
            log.error("❌ Failed to process Kafka message: {}", e.getMessage(), e);
        }
    }
}