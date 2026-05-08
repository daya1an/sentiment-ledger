package com.daya.project.sentiment_ledger.service.payment;

import com.daya.project.sentiment_ledger.model.Invoice;
import com.daya.project.sentiment_ledger.model.LedgerEntry;
import com.daya.project.sentiment_ledger.model.PaymentEvent;
import com.daya.project.sentiment_ledger.repository.InvoiceRepository;
import com.daya.project.sentiment_ledger.repository.LedgerEntryRepository;
import com.daya.project.sentiment_ledger.service.MetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class PaymentEventListener {

    private final PaymentService paymentService;
    private final InvoiceRepository invoiceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final MetricsService metricsService;

    public PaymentEventListener(PaymentService paymentService,
                                InvoiceRepository invoiceRepository,
                                LedgerEntryRepository ledgerEntryRepository,
                                MetricsService metricsService) {
        this.paymentService = paymentService;
        this.invoiceRepository = invoiceRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.metricsService = metricsService;
    }

    @KafkaListener(
            topics = "payment-executed",
            groupId = "payment-processing-group",
            containerFactory = "paymentEventKafkaListenerContainerFactory"
    )
    public void handlePaymentEvent(PaymentEvent event) {
        log.info("💳 Processing payment event for invoice: {}", event.getInvoiceId());

        Optional<Invoice> invoiceOpt = invoiceRepository.findById(event.getInvoiceId());
        if (invoiceOpt.isEmpty()) {
            log.error("❌ Invoice not found: {}", event.getInvoiceId());
            return;
        }

        Invoice invoice = invoiceOpt.get();

        if ("REJECTED".equals(event.getDecision())) {
            invoice.setStatus(Invoice.Status.REJECTED);
            invoiceRepository.save(invoice);
            ledgerEntryRepository.save(new LedgerEntry(event.getInvoiceId(), "REJECTED", "Policy rejection"));
            metricsService.incrementRejectedInvoices();
            log.info("❌ Invoice {} rejected", event.getInvoiceId());
            return;
        }

        if ("MANUAL_REVIEW".equals(event.getDecision())) {
            invoice.setStatus(Invoice.Status.PENDING);
            invoiceRepository.save(invoice);
            metricsService.incrementManualReviewInvoices();
            log.info("👤 Invoice {} flagged for manual review", event.getInvoiceId());
            return;
        }

        // Execute payment for APPROVED
        if ("APPROVED".equals(event.getDecision())) {
            try {
                String transactionId = paymentService.executePayout(event.getInvoiceId(), event.getAmount());

                invoice.setStatus(Invoice.Status.PAID);
                invoiceRepository.save(invoice);

                ledgerEntryRepository.save(new LedgerEntry(
                        event.getInvoiceId(),
                        "PAID",
                        "Transaction ID: " + transactionId
                ));

                metricsService.recordPaymentSuccess(event.getAmount());
                log.info("✅ Payment successful for invoice {}. Transaction ID: {}",
                        event.getInvoiceId(), transactionId);

            } catch (Exception e) {
                log.error("❌ Payment failed for invoice {}: {}", event.getInvoiceId(), e.getMessage());
                invoice.setStatus(Invoice.Status.PENDING);
                invoiceRepository.save(invoice);
                metricsService.incrementPaymentErrors();
            }
        }
    }
}