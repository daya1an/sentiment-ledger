package com.daya.project.sentiment_ledger.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.DistributionSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

/**
 * Service for recording Prometheus metrics on all critical paths
 * Metrics are exposed on /actuator/prometheus endpoint
 *
 * Tracks:
 * - Request latencies (p50, p95, p99)
 * - Duplicate detection rate
 * - Approval/rejection rates
 * - Payment success/failure
 * - Processing errors
 */
@Slf4j
@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    // ==================== Counters ====================

    public void incrementDuplicateInvoicesDetected() {
        Counter.builder("invoice.duplicates.detected")
                .description("Number of duplicate invoices detected by Redis lock")
                .tag("service", "sentiment-ledger")
                .register(meterRegistry)
                .increment();
    }

    public void incrementProcessingErrors() {
        Counter.builder("invoice.processing.errors")
                .description("Number of errors during invoice processing")
                .tag("service", "sentiment-ledger")
                .register(meterRegistry)
                .increment();
    }

    public void incrementRejectedInvoices() {
        Counter.builder("invoice.rejected")
                .description("Number of invoices rejected by AI")
                .tag("service", "sentiment-ledger")
                .register(meterRegistry)
                .increment();
    }

    public void incrementApprovedInvoices() {
        Counter.builder("invoice.approved")
                .description("Number of invoices approved by AI")
                .tag("service", "sentiment-ledger")
                .register(meterRegistry)
                .increment();
    }

    public void incrementPaymentErrors() {
        Counter.builder("payment.errors")
                .description("Number of payment processing failures")
                .tag("service", "sentiment-ledger")
                .register(meterRegistry)
                .increment();
    }

    public void incrementManualReviewInvoices() {
        Counter.builder("invoice.manual_review")
                .description("Number of invoices flagged for manual review")
                .tag("service", "sentiment-ledger")
                .register(meterRegistry)
                .increment();
    }

    public void recordKafkaMessageProcessed(String topic) {
        Counter.builder("kafka.messages.processed")
                .tag("topic", topic)
                .description("Kafka messages successfully processed")
                .tag("service", "sentiment-ledger")
                .register(meterRegistry)
                .increment();
    }

    // ==================== Timers ====================

    public void recordInvoiceProcessingLatency(long startTimeMillis) {
        long latency = System.currentTimeMillis() - startTimeMillis;
        Timer.builder("invoice.processing.latency")
                .description("Invoice processing latency in milliseconds")
                .tag("service", "sentiment-ledger")
                .publishPercentiles(0.5, 0.95, 0.99) // Track p50, p95, p99
                .register(meterRegistry)
                .record(latency, TimeUnit.MILLISECONDS);
    }

//    /**
//     * Record invoice processing latency (when you have latency already)
//     *
//     * @param latencyMillis Processing latency in milliseconds
//     */
//    public void recordInvoiceProcessingLatency(long latencyMillis) {
//        Timer.builder("invoice.processing.latency")
//                .description("Invoice processing latency in milliseconds")
//                .tag("service", "sentiment-ledger")
//                .publishPercentiles(0.5, 0.95, 0.99)
//                .register(meterRegistry)
//                .record(latencyMillis, TimeUnit.MILLISECONDS);
//    }

    public void recordPaymentSuccess() {
        Timer.builder("payment.success")
                .description("Successful payment execution latency")
                .tag("service", "sentiment-ledger")
                .register(meterRegistry)
                .record(1, TimeUnit.SECONDS);
    }

    public void recordAIDecisionLatency(long latencyMillis) {
        Timer.builder("ai.decision.latency")
                .description("Time taken by AI to make approval decision")
                .tag("service", "sentiment-ledger")
                .tag("model", "gemini")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(latencyMillis, TimeUnit.MILLISECONDS);
    }

    public void recordPolicyRetrievalLatency(long latencyMillis) {
        Timer.builder("policy.retrieval.latency")
                .description("Time taken to retrieve policies from vector store")
                .tag("service", "sentiment-ledger")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(latencyMillis, TimeUnit.MILLISECONDS);
    }

    // ==================== Gauges & Distribution Summaries ====================

    public void recordPaymentSuccess(BigDecimal amount) {
        DistributionSummary.builder("payment.amount.processed")
                .description("Amount of money successfully processed")
                .baseUnit("rupees")
                .tag("service", "sentiment-ledger")
                .register(meterRegistry)
                .record(amount.doubleValue());
    }

    public void recordAIDecisionConfidence(double confidence) {
        DistributionSummary.builder("ai.decision.confidence")
                .description("AI confidence in approval decision (0.0-1.0)")
                .tag("service", "sentiment-ledger")
                .register(meterRegistry)
                .record(confidence);
    }

    public void recordMongoDBQueryLatency(long latencyMillis) {
        Timer.builder("mongodb.query.latency")
                .description("MongoDB query execution latency")
                .tag("service", "sentiment-ledger")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(latencyMillis, TimeUnit.MILLISECONDS);
    }

    public void recordRedisOperationLatency(long latencyMillis) {
        Timer.builder("redis.operation.latency")
                .description("Redis operation latency")
                .tag("service", "sentiment-ledger")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(latencyMillis, TimeUnit.MILLISECONDS);
    }

    public void recordKafkaPublishLatency(long latencyMillis) {
        Timer.builder("kafka.publish.latency")
                .description("Kafka message publish latency")
                .tag("service", "sentiment-ledger")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(latencyMillis, TimeUnit.MILLISECONDS);
    }

    public void recordAPIRequestLatency(long latencyMillis) {
        Timer.builder("api.request.latency")
                .description("API request latency")
                .tag("service", "sentiment-ledger")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(latencyMillis, TimeUnit.MILLISECONDS);
    }

    public void logMetricsSummary() {
        try {
            Counter duplicatesCounter = meterRegistry.find("invoice.duplicates.detected").counter();
            Counter errorsCounter = meterRegistry.find("invoice.processing.errors").counter();
            Counter approvedCounter = meterRegistry.find("invoice.approved").counter();
            Counter rejectedCounter = meterRegistry.find("invoice.rejected").counter();

            double duplicates = duplicatesCounter != null ? duplicatesCounter.count() : 0.0;
            double errors = errorsCounter != null ? errorsCounter.count() : 0.0;
            double approved = approvedCounter != null ? approvedCounter.count() : 0.0;
            double rejected = rejectedCounter != null ? rejectedCounter.count() : 0.0;

            log.info("📊 Metrics Summary | Duplicates: {} | Errors: {} | Approved: {} | Rejected: {}",
                    duplicates, errors, approved, rejected);

        } catch (Exception e) {
            log.debug("Error logging metrics: {}", e.getMessage());
        }
    }

}