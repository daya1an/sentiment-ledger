package com.daya.project.sentiment_ledger.controller;

import com.daya.project.sentiment_ledger.config.KafkaTopicConfig;
import com.daya.project.sentiment_ledger.model.Invoice;
import com.daya.project.sentiment_ledger.repository.InvoiceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/invoices")
public class InvoiceController {

    private final InvoiceRepository invoiceRepository;

    // Constructor injection is standard practice in modern Spring Boot
    public InvoiceController(InvoiceRepository invoiceRepository, KafkaTemplate<String, Object> kafkaTemplate) {
        this.invoiceRepository = invoiceRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    // Spring Kafka's tool for producing messages
    private final KafkaTemplate<String, Object> kafkaTemplate;


    @PostMapping
    public ResponseEntity<String> submitInvoice(@RequestBody Invoice invoice) {
        // Since we aren't saving to Mongo immediately, we generate a temporary ID
        if (invoice.getId() == null) {
            invoice.setId(UUID.randomUUID().toString());
        }

        log.info("API received invoice for vendor: {}", invoice.getVendorName());

        // Publish the event to Kafka! (Key = Invoice ID, Value = The complete object)
        kafkaTemplate.send(KafkaTopicConfig.INVOICE_SUBMITTED_TOPIC, invoice.getId(), invoice);

        // Return a 202 Accepted, letting the user know it's in the queue
        return ResponseEntity.accepted().body("Invoice accepted into the processing queue. Event ID: " + invoice.getId());
    }

    @GetMapping
    public ResponseEntity<List<Invoice>> getAllInvoices() {
        return ResponseEntity.ok(invoiceRepository.findAll());
    }
}