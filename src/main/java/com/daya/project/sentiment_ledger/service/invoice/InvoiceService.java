package com.daya.project.sentiment_ledger.service.invoice;

import com.daya.project.sentiment_ledger.config.KafkaTopicConfig;
import com.daya.project.sentiment_ledger.model.IdempotencyKey;
import com.daya.project.sentiment_ledger.model.Invoice;
import com.daya.project.sentiment_ledger.repository.IdempotencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private final IdempotencyRepository idempotencyRepository;
    private final KafkaTemplate<String, Invoice> kafkaTemplate;

    public String submitInvoice(Invoice invoice, String idempotencyKey) {

        log.info("📨 API request received | Vendor: {} | Amount: {} | Idempotency-Key: {}",
                invoice.getVendorName(), invoice.getAmount(), idempotencyKey);

        // Step 1: Check if request was already processed
        Optional<IdempotencyKey> existingRequest = idempotencyRepository.findByClientProvidedKey(idempotencyKey);
        if (existingRequest.isPresent()) {
            IdempotencyKey existing = existingRequest.get();

            if (existing.isValid()) {
                log.info("✅ IDEMPOTENT RETRY DETECTED | Returning cached response for key: {}", idempotencyKey);
                return existing.getResponseBody();
            } else {
                log.warn("⚠️ Idempotency key expired, processing new request");
            }
        }

        // Step 2: Generate invoice ID if not provided
        if (invoice.getId() == null) {
            invoice.setId(UUID.randomUUID().toString());
        }

        log.info("✅ Processing new invoice | ID: {} | Vendor: {}", invoice.getId(), invoice.getVendorName());

        // Step 3: Create response message
        String responseMessage = "Invoice accepted into the processing queue. Event ID: " + invoice.getId();

        // Step 4: Store idempotency record BEFORE publishing to Kafka
        IdempotencyKey idempotencyRecord = new IdempotencyKey(
                idempotencyKey,
                invoice.getId(),
                responseMessage
        );
        idempotencyRepository.save(idempotencyRecord);
        log.debug("💾 Idempotency record saved for key: {}", idempotencyKey);

        // Step 5: Publish invoice to Kafka for async processing
        try {
            kafkaTemplate.send(KafkaTopicConfig.INVOICE_SUBMITTED_TOPIC, invoice.getId(), invoice);
            log.info("📤 Invoice published to Kafka topic: {}", KafkaTopicConfig.INVOICE_SUBMITTED_TOPIC);
        } catch (Exception e) {
            log.error("❌ Failed to publish invoice to Kafka: {}", e.getMessage());
            idempotencyRecord.setResponseStatus("FAILED");
            idempotencyRepository.save(idempotencyRecord);
        }

        return responseMessage;
    }
}
