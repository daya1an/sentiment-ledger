package com.daya.project.sentiment_ledger.repository;

import com.daya.project.sentiment_ledger.model.Invoice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceRepository extends MongoRepository<Invoice, String> {

    List<Invoice> findByStatus(Invoice.Status status);

}
