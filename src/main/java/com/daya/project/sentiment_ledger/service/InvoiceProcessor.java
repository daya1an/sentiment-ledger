package com.daya.project.sentiment_ledger.service;

import com.daya.project.sentiment_ledger.config.KafkaTopicConfig;
import com.daya.project.sentiment_ledger.model.Invoice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class InvoiceProcessor {

    private final StringRedisTemplate redisTemplate;
    private final PolicyRetrievalService policyRetrievalService;

    public InvoiceProcessor(StringRedisTemplate redisTemplate, PolicyRetrievalService policyRetrievalService) {
        this.redisTemplate = redisTemplate;
        this.policyRetrievalService = policyRetrievalService;
    }

    @KafkaListener(topics = "invoice-submitted", groupId = "ledger-processing-group")
    public void processInvoice(Invoice invoice) {

//        log.info("🔥 KAFKA EVENT RECEIVED for Invoice ID: {}", invoice.getId());
//
//        String lockKey = "invoice:lock:" + invoice.getId();
//
//        Boolean isNewInvoice = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", Duration.ofHours(24));
//
//        if (Boolean.FALSE.equals(isNewInvoice)) {
//            log.warn("⚠️ DUPLICATE DETECTED: Invoice ID {} is already locked. Dropping message.", invoice.getId());
//            return;
//        }

        log.info("✅ Lock acquired. Processing Invoice ID: {}", invoice.getId());

        // Dynamically get the category from the Kafka message
        String category = invoice.getCategory();

        // 🧪 TEST THE RAG SERVICE 🧪
        log.info("🧠 Asking Vector Store for rules regarding: {}", category);
        String policies = policyRetrievalService.getPolicyContext(category);

        log.info("📄 RETRIEVED POLICIES:\n{}", policies);
    }
}