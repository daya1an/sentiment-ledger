package com.daya.project.sentiment_ledger.controller;

import com.daya.project.sentiment_ledger.dto.AIDecisionStats;
import com.daya.project.sentiment_ledger.model.Invoice;
import com.daya.project.sentiment_ledger.repository.InvoiceRepository;
import com.daya.project.sentiment_ledger.repository.LedgerEntryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/analytics")
@Slf4j
public class AIAnalyticsController {

    private final InvoiceRepository invoiceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public AIAnalyticsController(InvoiceRepository invoiceRepository,
                                 LedgerEntryRepository ledgerEntryRepository) {
        this.invoiceRepository = invoiceRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @GetMapping("/decision-stats")
    public ResponseEntity<AIDecisionStats> getDecisionStats(
            @RequestParam(defaultValue = "7") int days) {

        Instant since = Instant.now().minus(Duration.ofDays(days));

        List<Invoice> recentInvoices = invoiceRepository.findByCreatedAtAfter(since);

        long approved = recentInvoices.stream()
                .filter(inv -> inv.getStatus() == Invoice.Status.AI_APPROVED)
                .count();

        long rejected = recentInvoices.stream()
                .filter(inv -> inv.getStatus() == Invoice.Status.REJECTED)
                .count();

        long manual = recentInvoices.stream()
                .filter(inv -> inv.getStatus() == Invoice.Status.PENDING)
                .count();

        double avgConfidence = recentInvoices.stream()
                .mapToDouble(Invoice::getConfidenceScore)
                .average()
                .orElse(0.0);

        AIDecisionStats stats = new AIDecisionStats(
                recentInvoices.size(),
                approved,
                rejected,
                manual,
                (double) approved / recentInvoices.size() * 100,
                avgConfidence
        );

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/low-confidence-decisions")
    public ResponseEntity<List<Invoice>> getLowConfidenceDecisions(
            @RequestParam(defaultValue = "0.7") double threshold) {

        List<Invoice> lowConfidence = invoiceRepository.findByConfidenceScoreLessThan(threshold);
        return ResponseEntity.ok(lowConfidence);
    }
}
