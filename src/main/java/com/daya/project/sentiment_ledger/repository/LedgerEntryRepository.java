package com.daya.project.sentiment_ledger.repository;

import com.daya.project.sentiment_ledger.model.LedgerEntry;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerEntryRepository extends MongoRepository<LedgerEntry, String> {
    List<LedgerEntry> findByInvoiceId(String invoiceId);
}
