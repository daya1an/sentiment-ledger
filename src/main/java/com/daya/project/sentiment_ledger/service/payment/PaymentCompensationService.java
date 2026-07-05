package com.daya.project.sentiment_ledger.service.payment;

import com.daya.project.sentiment_ledger.model.Invoice;
import com.daya.project.sentiment_ledger.model.LedgerEntry;
import com.daya.project.sentiment_ledger.repository.InvoiceRepository;
import com.daya.project.sentiment_ledger.repository.LedgerEntryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Handles compensation when payment service is down
 * Retries pending transactions when service recovers
 */
@Service
@Slf4j
public class PaymentCompensationService {

    private final InvoiceRepository invoiceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PaymentService paymentService;

    public PaymentCompensationService(
            InvoiceRepository invoiceRepository,
            LedgerEntryRepository ledgerEntryRepository,
            PaymentService paymentService) {
        this.invoiceRepository = invoiceRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.paymentService = paymentService;
    }

    /**
     * Retry pending payments every 5 minutes
     * Checks if payment service recovered
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void retryPendingPayments() {
        log.info("🔄 Checking pending payments...");

        List<Invoice> pendingInvoices = invoiceRepository.findByStatus(Invoice.Status.PENDING);

        pendingInvoices.forEach(invoice -> {
            try {
                log.info("📤 Retrying payment for invoice: {}", invoice.getId());
                
                // Validate vendor Stripe Connect ID exists
                if (invoice.getVendorStripeConnectId() == null || invoice.getVendorStripeConnectId().trim().isEmpty()) {
                    log.warn("⚠️ Cannot retry payment for invoice {} - vendor Stripe Connect ID missing", invoice.getId());
                    return;
                }
                
                String txnId = paymentService.executePayout(
                        invoice.getId(), 
                        invoice.getAmount(), 
                        invoice.getVendorName(),
                        invoice.getVendorStripeConnectId()
                );

                // Update status
                invoice.setStatus(Invoice.Status.PAID);
                invoiceRepository.save(invoice);

                // Log successful retry
                ledgerEntryRepository.save(new LedgerEntry(
                        invoice.getId(),
                        "PAID_RETRY",
                        "Retry successful. Vendor " + invoice.getVendorName() + " credited. Transfer ID: " + txnId
                ));

                log.info("✅ Payment retry successful for invoice: {}", invoice.getId());

            } catch (Exception e) {
                log.warn("⚠️ Payment retry still failing for invoice: {}", invoice.getId());
                // Will retry again in 5 minutes
            }
        });
    }

    /**
     * Mark invoices as failed after 3 retry attempts
     */
    @Scheduled(fixedRate = 600000) // 10 minutes
    public void escalateFailedPayments() {
        log.info("🚨 Checking for escalation...");

        // In production: Count retries, mark as FAILED after threshold
        // Send alert to admin
    }
}