package com.daya.project.sentiment_ledger.repository;

import com.daya.project.sentiment_ledger.model.Invoice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface InvoiceRepository extends MongoRepository<Invoice, String> {

    List<Invoice> findByStatus(Invoice.Status status);

    // Index: vendorName ASC
    List<Invoice> findByVendorName(String vendorName);

    // Index: status ASC, createdAt DESC
    List<Invoice> findByStatusOrderByCreatedAtDesc(Invoice.Status status);

    // Custom query with hint for index
    @Query("{ 'status': ?0, 'createdAt': { $gte: ?1 } }")
    List<Invoice> findRecentInvoicesByStatus(Invoice.Status status, Instant since);

}
