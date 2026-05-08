package com.daya.project.sentiment_ledger.controller;

import com.daya.project.sentiment_ledger.model.Invoice;
import com.daya.project.sentiment_ledger.repository.InvoiceRepository;
import com.daya.project.sentiment_ledger.service.invoice.InvoiceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/invoices")
public class InvoiceController {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;

    public InvoiceController(
            InvoiceRepository invoiceRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            InvoiceService invoiceService) {
        this.invoiceRepository = invoiceRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.invoiceService = invoiceService;
    }

    @PostMapping
    public ResponseEntity<String> submitInvoice(
            @RequestBody Invoice invoice,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey) {

        String responseMessage = invoiceService.submitInvoice(invoice, idempotencyKey);
        return ResponseEntity.accepted().body(responseMessage);
    }

    @GetMapping
    public ResponseEntity<List<Invoice>> getAllInvoices() {
        return ResponseEntity.ok(invoiceRepository.findAll());
    }
}