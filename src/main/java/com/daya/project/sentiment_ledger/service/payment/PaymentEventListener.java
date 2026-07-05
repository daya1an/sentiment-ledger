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
        // Inside PaymentEventListener.java -> handlePaymentEvent(PaymentEvent event)

        if ("APPROVED".equals(event.getDecision())) {
            try {
                // Validate vendor has Stripe Connect ID
                if (invoice.getVendorStripeConnectId() == null || invoice.getVendorStripeConnectId().trim().isEmpty()) {
                    log.error("❌ Vendor {} missing Stripe Connect ID. Cannot process payment for invoice {}", 
                            invoice.getVendorName(), event.getInvoiceId());
                    invoice.setStatus(Invoice.Status.PENDING);
                    invoiceRepository.save(invoice);
                    ledgerEntryRepository.save(new LedgerEntry(
                            event.getInvoiceId(),
                            "PAYMENT_FAILED",
                            "Vendor Stripe Connect ID is missing"
                    ));
                    metricsService.incrementPaymentErrors();
                    return;
                }

                // 1. Execute Stripe transfer to vendor
                String transferId = paymentService.executePayout(
                        event.getInvoiceId(),
                        event.getAmount(),
                        invoice.getVendorName(),
                        invoice.getVendorStripeConnectId()
                );

                // 2. Update Database Status
                invoice.setStatus(Invoice.Status.PAID);
                invoiceRepository.save(invoice);

                // 3. Generate Bill / Receipt Content
                String generatedBill = String.format(
                        "====================================\n" +
                                "       SENTIMENT LEDGER BILL        \n" +
                                "====================================\n" +
                                "Invoice ID:    %s\n" +
                                "Vendor:        %s\n" +
                                "Vendor ConnectId: %s\n" +
                                "Amount Paid:   $%.2f\n" +
                                "Status:        PAID\n" +
                                "Transfer ID:   %s\n" +
                                "Date:          %s\n" +
                                "====================================",
                        invoice.getId(), invoice.getVendorName(), invoice.getVendorStripeConnectId(), 
                        event.getAmount(), transferId, java.time.Instant.now()
                );

                log.info("\n{}\n", generatedBill);

                // 4. Save to immutable ledger
                ledgerEntryRepository.save(new LedgerEntry(
                        event.getInvoiceId(),
                        "PAID",
                        "Stripe Transfer Successful. Vendor " + invoice.getVendorName() + " credited. Transfer ID: " + transferId + ". Bill Generated."
                ));

                metricsService.recordPaymentSuccess(event.getAmount());

            } catch (Exception e) {
                log.error("❌ Payment execution failed for invoice {}: {}", event.getInvoiceId(), e.getMessage());
                invoice.setStatus(Invoice.Status.PENDING);
                invoiceRepository.save(invoice);
                metricsService.incrementPaymentErrors();
            }
        }
    }
}