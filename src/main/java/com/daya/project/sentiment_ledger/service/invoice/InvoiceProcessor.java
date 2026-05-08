package com.daya.project.sentiment_ledger.service.invoice;

import com.daya.project.sentiment_ledger.config.KafkaTopicConfig;
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

    /**
     * Kafka listener for invoice-submitted events
     * Processes invoices with AI decision-making and audit trail creation
     *
     * Uses Redis distributed lock to prevent duplicate processing
     * even though Kafka guarantees at-least-once delivery
     *
     * @param invoice Invoice to process
     */
    @KafkaListener(topics = "invoice-submitted", groupId = "ledger-processing-group")
    public void processInvoice(Invoice invoice) {

        // Generate unique lock key and value for this invoice
        String lockKey = "invoice:lock:" + invoice.getId();
        String lockValue = UUID.randomUUID().toString();

        long startTime = System.currentTimeMillis();

        // Try to acquire distributed lock
        // setIfAbsent: only sets if key doesn't exist
        // Duration.ofHours(24): TTL prevents zombie locks
        Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofHours(24));

        // If lock already held, this is a duplicate message - drop it
        if (Boolean.FALSE.equals(lockAcquired)) {
            log.warn("⚠️ DUPLICATE DETECTED: Invoice ID {} already being processed. Dropping message.",
                    invoice.getId());
            metricsService.incrementDuplicateInvoicesDetected();
            return;
        }

        try {
            log.info("✅ Lock acquired for Invoice ID: {}", invoice.getId());

            // Initialize invoice metadata
            invoice.setStatus(Invoice.Status.PENDING);
            invoice.setCreatedAt(Instant.now());

            // Step 1: Retrieve relevant policies from vector store (RAG)
            String category = invoice.getCategory();
            log.info("🧠 Retrieving policies for category: {}", category);
            String policies = policyRetrievalService.getPolicyContext(category);

            // Step 2: Get AI decision based on policies
            String decision = aiDecisionService.getApprovalDecision(invoice, policies);

            // Step 3: Update invoice status based on AI decision
            updateInvoiceStatus(invoice, decision);

            // Step 4: Persist invoice to MongoDB
            invoiceRepository.save(invoice);
            log.debug("💾 Invoice saved to MongoDB");

            // Step 5: Create immutable audit ledger entry with reasoning
            ledgerEntryRepository.save(new LedgerEntry(
                    invoice.getId(),
                    decision,
                    aiDecisionService.getReasoningContext()
            ));
            log.debug("📝 Audit ledger entry created");

            // Step 6: Publish payment event for async payment processing
            kafkaTemplate.send(
                    KafkaTopicConfig.PAYMENT_EXECUTED_TOPIC,
                    invoice.getId(),
                    new PaymentEvent(invoice.getId(), invoice.getAmount(), decision)
            );
            log.debug("📤 Payment event published to Kafka");

            // Step 7: Record metrics
            long processingTime = System.currentTimeMillis() - startTime;
            metricsService.recordInvoiceProcessingLatency(processingTime);
            metricsService.recordKafkaMessageProcessed("invoice-submitted");

            log.info("✅ Invoice {} processed successfully in {}ms. Decision: {}",
                    invoice.getId(), processingTime, decision);

        } catch (Exception e) {
            log.error("❌ Error processing invoice {}: {}", invoice.getId(), e.getMessage(), e);

            // Mark invoice as pending for retry
            invoice.setStatus(Invoice.Status.PENDING);
            invoiceRepository.save(invoice);

            // Record error metrics
            metricsService.incrementProcessingErrors();

            // Re-throw to let Kafka handle retry
            throw e;

        } finally {
            // Always release lock in finally block to prevent deadlocks
            String storedValue = redisTemplate.opsForValue().get(lockKey);

            // Only delete if we own the lock (value matches)
            // Prevents accidentally releasing someone else's lock
            if (lockValue.equals(storedValue)) {
                redisTemplate.delete(lockKey);
                log.debug("🔓 Lock released for Invoice ID: {}", invoice.getId());
            } else {
                log.warn("⚠️ Lock value mismatch for invoice {}. Not releasing.", invoice.getId());
            }

            // Clear AI reasoning context (ThreadLocal cleanup)
            aiDecisionService.clearReasoningContext();
        }
    }

    /**
     * Update invoice status based on AI decision
     *
     * @param invoice Invoice to update
     * @param decision AI decision string: APPROVED, REJECTED, MANUAL_REVIEW
     */
    private void updateInvoiceStatus(Invoice invoice, String decision) {
        switch (decision.trim().toUpperCase()) {
            case "APPROVED" -> {
                invoice.setStatus(Invoice.Status.AI_APPROVED);
                log.info("✅ Invoice {} APPROVED by AI", invoice.getId());
            }
            case "REJECTED" -> {
                invoice.setStatus(Invoice.Status.REJECTED);
                log.info("❌ Invoice {} REJECTED by AI", invoice.getId());
            }
            case "MANUAL_REVIEW" -> {
                invoice.setStatus(Invoice.Status.PENDING);
                log.warn("👤 Invoice {} flagged for MANUAL_REVIEW", invoice.getId());
            }
            default -> {
                invoice.setStatus(Invoice.Status.PENDING);
                log.warn("⚠️ Unknown decision '{}' for invoice {}. Defaulting to PENDING",
                        decision, invoice.getId());
            }
        }
    }
}