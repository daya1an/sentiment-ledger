package com.daya.project.sentiment_ledger.service;

import com.daya.project.sentiment_ledger.repository.IdempotencyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Background job to clean up expired idempotency records
 * Runs every day at 2 AM to avoid peak hours
 */
@Slf4j
@Service
@EnableScheduling
public class IdempotencyCleanupService {

    private final IdempotencyRepository idempotencyRepository;

    public IdempotencyCleanupService(IdempotencyRepository idempotencyRepository) {
        this.idempotencyRepository = idempotencyRepository;
    }

    /**
     * Delete expired idempotency records daily
     * Idempotency records expire after 24 hours
     * Running this prevents unbounded MongoDB growth
     */
    @Scheduled(cron = "0 0 2 * * *") // Every day at 2 AM
    public void cleanupExpiredIdempotencyKeys() {
        log.info("🧹 Starting cleanup of expired idempotency records");

        try {
            Instant cutoffTime = Instant.now();
            long deletedCount = idempotencyRepository.deleteByExpiresAtBefore(cutoffTime);

            log.info("✅ Cleanup complete. Deleted {} expired idempotency records", deletedCount);

        } catch (Exception e) {
            log.error("❌ Error during idempotency cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Log idempotency stats every hour for monitoring
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void logIdempotencyStats() {
        try {
            long totalRecords = idempotencyRepository.count();
            long pendingRecords = idempotencyRepository.findByResponseStatus("PENDING").size();
            long failedRecords = idempotencyRepository.findByResponseStatus("FAILED").size();

            log.info("📊 Idempotency Stats | Total: {} | Pending: {} | Failed: {}",
                    totalRecords, pendingRecords, failedRecords);
        } catch (Exception e) {
            log.error("Error fetching idempotency stats: {}", e.getMessage());
        }
    }
}