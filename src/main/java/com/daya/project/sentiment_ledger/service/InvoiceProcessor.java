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
    private final AIDecisionService aiDecisionService;

    public InvoiceProcessor(StringRedisTemplate redisTemplate, PolicyRetrievalService policyRetrievalService, AIDecisionService aiDecisionService) {
        this.redisTemplate = redisTemplate;
        this.policyRetrievalService = policyRetrievalService;
        this.aiDecisionService = aiDecisionService;
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

        String category = invoice.getCategory();
        log.info("🧠 Asking Vector Store for rules regarding: {}", category);
        String policies = policyRetrievalService.getPolicyContext(category);

//        log.info("📄 RETRIEVED POLICIES:\n{}", policies);

        // NEW: Call AI for decision
        String decision = aiDecisionService.getApprovalDecision(invoice, policies);

        log.info("📋 DECISION: {}", decision);
    }
}