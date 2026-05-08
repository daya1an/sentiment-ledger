package com.daya.project.sentiment_ledger.config;

import com.daya.project.sentiment_ledger.model.Invoice;
import com.daya.project.sentiment_ledger.model.LedgerEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@Slf4j
@Configuration
public class MongoIndexConfiguration implements InitializingBean {

    private final MongoTemplate mongoTemplate;

    public MongoIndexConfiguration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        // Index for finding invoices by status
        mongoTemplate.indexOps(Invoice.class)
                .createIndex(new Index().on("status", Sort.Direction.ASC));

        // Index for finding invoices by vendor
        mongoTemplate.indexOps(Invoice.class)
                .createIndex(new Index().on("vendorName", Sort.Direction.ASC));

        // Compound index for finding pending invoices by creation time
        mongoTemplate.indexOps(Invoice.class)
                .createIndex(new Index()
                        .on("status", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC));

        // Index for ledger entries by invoiceId
        mongoTemplate.indexOps(LedgerEntry.class)
                .createIndex(new Index().on("invoiceId", Sort.Direction.ASC));

        // Index for ledger entries by timestamp range queries
        mongoTemplate.indexOps(LedgerEntry.class)
                .createIndex(new Index().on("timestamp", Sort.Direction.DESC));

        // Compound index for audit queries
        mongoTemplate.indexOps(LedgerEntry.class)
                .createIndex(new Index()
                        .on("invoiceId", Sort.Direction.ASC)
                        .on("timestamp", Sort.Direction.DESC));

        log.info("✅ MongoDB indexes created successfully");
    }
}
