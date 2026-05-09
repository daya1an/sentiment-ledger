package com.daya.project.sentiment_ledger.service.invoice;

import com.daya.project.sentiment_ledger.config.kafka.KafkaTopicConfig;
import com.daya.project.sentiment_ledger.model.AIApprovalDecision;
import com.daya.project.sentiment_ledger.model.Invoice;
import com.daya.project.sentiment_ledger.model.LedgerEntry;
import com.daya.project.sentiment_ledger.model.PaymentEvent;
import com.daya.project.sentiment_ledger.repository.InvoiceRepository;
import com.daya.project.sentiment_ledger.repository.LedgerEntryRepository;
import com.daya.project.sentiment_ledger.service.AIDecisionService;
import com.daya.project.sentiment_ledger.service.MetricsService;
import com.daya.project.sentiment_ledger.service.policy.PolicyRetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class InvoiceProcessor {

    private final StringRedisTemplate redisTemplate;
    private final PolicyRetrievalService policyRetrievalService;
    private final AIDecisionService aiDecisionService;
    private final InvoiceRepository invoiceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MetricsService metricsService;

    public InvoiceProcessor(
            StringRedisTemplate redisTemplate,
            PolicyRetrievalService policyRetrievalService,
            AIDecisionService aiDecisionService,
            InvoiceRepository invoiceRepository,
            LedgerEntryRepository ledgerEntryRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            MetricsService metricsService) {

        this.redisTemplate = redisTemplate;
        this.policyRetrievalService = policyRetrievalService;
        this.aiDecisionService = aiDecisionService;
        this.invoiceRepository = invoiceRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.metricsService = metricsService;
    }

    @KafkaListener(topics = "invoice-submitted", groupId = "ledger-processing-group")
    public void processInvoice(Invoice invoice) {

        String lockKey = "invoice:lock:" + invoice.getId();
        String lockValue = UUID.randomUUID().toString();

        Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofHours(24));

        if (Boolean.FALSE.equals(lockAcquired)) {
            log.warn("⚠️ DUPLICATE: Invoice {} already being processed", invoice.getId());
            metricsService.incrementDuplicateInvoicesDetected();
            return;
        }

        try {
            log.info("✅ Processing Invoice ID: {}", invoice.getId());

            invoice.setStatus(Invoice.Status.PENDING);
            invoice.setCreatedAt(Instant.now());

            String category = invoice.getCategory();
            String policies = policyRetrievalService.getPolicyContext(category);

            // Get structured AI decision
            AIApprovalDecision decision = aiDecisionService.getApprovalDecision(invoice, policies);

            // Update invoice status based on decision
            updateInvoiceStatus(invoice, decision);

            // Log risk flags
            if (!decision.getRiskFlags().isEmpty()) {
                log.warn("⚠️ Risk flags detected for invoice {}: {}",
                        invoice.getId(), decision.getRiskFlags());
            }

            // Save invoice with decision metadata
            invoice.setConfidenceScore(decision.getConfidence());
            invoiceRepository.save(invoice);

            // Create detailed ledger entry
            LedgerEntry ledgerEntry = new LedgerEntry(
                    invoice.getId(),
                    decision.getMainDecision(),
                    String.format(
                            "Decision: %s | Confidence: %.2f | Reasoning: %s | Risk Flags: %s | Approval Level: %s",
                            decision.getDecision(),
                            decision.getConfidence(),
                            decision.getReasoning(),
                            String.join(", ", decision.getRiskFlags()),
                            decision.getRequiresApprovalLevel()
                    )
            );
            ledgerEntryRepository.save(ledgerEntry);

            // Publish event
            kafkaTemplate.send(KafkaTopicConfig.PAYMENT_EXECUTED_TOPIC,
                    invoice.getId(),
                    new PaymentEvent(invoice.getId(), invoice.getAmount(), decision.getMainDecision()));

            metricsService.recordAIDecisionConfidence(decision.getConfidence());
            metricsService.incrementApprovedInvoices();

        } catch (Exception e) {
            log.error("❌ Error processing invoice {}: {}", invoice.getId(), e.getMessage(), e);
            invoice.setStatus(Invoice.Status.PENDING);
            invoiceRepository.save(invoice);
            metricsService.incrementProcessingErrors();
        } finally {
            String storedValue = redisTemplate.opsForValue().get(lockKey);
            if (lockValue.equals(storedValue)) {
                redisTemplate.delete(lockKey);
            }
        }
    }

    private void updateInvoiceStatus(Invoice invoice, AIApprovalDecision decision) {
        switch (decision.getMainDecision()) {
            case "APPROVED" -> invoice.setStatus(Invoice.Status.AI_APPROVED);
            case "REJECTED" -> invoice.setStatus(Invoice.Status.REJECTED);
            case "MANUAL_REVIEW" -> invoice.setStatus(Invoice.Status.PENDING);
            default -> invoice.setStatus(Invoice.Status.PENDING);
        }
    }
}