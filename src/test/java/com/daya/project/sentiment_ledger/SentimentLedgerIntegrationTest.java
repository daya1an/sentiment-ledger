package com.daya.project.sentiment_ledger;

import com.daya.project.sentiment_ledger.model.Invoice;
import com.daya.project.sentiment_ledger.model.IdempotencyKey;
import com.daya.project.sentiment_ledger.model.LedgerEntry;
import com.daya.project.sentiment_ledger.repository.InvoiceRepository;
import com.daya.project.sentiment_ledger.repository.IdempotencyRepository;
import com.daya.project.sentiment_ledger.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public class SentimentLedgerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Container
    static MongoDBContainer mongoContainer = new MongoDBContainer("mongo:latest")
            .withExposedPorts(27017);

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:latest")
            .withExposedPorts(6379);

    @Container
    static GenericContainer<?> kafkaContainer = new GenericContainer<>("confluentinc/cp-kafka:latest")
            .withExposedPorts(9092)
            .withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://localhost:9092")
            .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "PLAINTEXT:PLAINTEXT")
            .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "PLAINTEXT")
            .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1");

    private Invoice testInvoice;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        invoiceRepository.deleteAll();
        idempotencyRepository.deleteAll();
        ledgerEntryRepository.deleteAll();

        testInvoice = new Invoice();
        testInvoice.setVendorName("Google Cloud");
        testInvoice.setAmount(new BigDecimal("3000.00"));
        testInvoice.setCategory("INFRASTRUCTURE");

        idempotencyKey = UUID.randomUUID().toString();
    }

    @Test
    void testHealthCheck() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testSubmitInvoiceWithIdempotencyKey() {
        // Arrange
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        headers.set("Content-Type", "application/json");
        HttpEntity<Invoice> request = new HttpEntity<>(testInvoice, headers);

        // Act
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/invoices",
                request,
                String.class
        );

        // Assert
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertTrue(response.getBody().contains("Event ID"));
        assertTrue(response.getBody().contains("accepted into the processing queue"));

        // Verify idempotency record saved
        IdempotencyKey record = idempotencyRepository.findByClientProvidedKey(idempotencyKey).orElse(null);
        assertNotNull(record);
        assertEquals(idempotencyKey, record.getClientProvidedKey());
        assertTrue(record.isValid());
    }

    @Test
    void testRetryWithSameIdempotencyKeyReturnsSameResponse() throws InterruptedException {
        // Arrange
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        headers.set("Content-Type", "application/json");
        HttpEntity<Invoice> request = new HttpEntity<>(testInvoice, headers);

        // Act - First request
        ResponseEntity<String> response1 = restTemplate.postForEntity(
                "/invoices",
                request,
                String.class
        );

        String body1 = response1.getBody();
        String eventId1 = extractEventId(body1);

        // Wait a bit
        Thread.sleep(500);

        // Act - Retry with same key
        ResponseEntity<String> response2 = restTemplate.postForEntity(
                "/invoices",
                request,
                String.class
        );

        String body2 = response2.getBody();
        String eventId2 = extractEventId(body2);

        // Assert
        assertEquals(HttpStatus.ACCEPTED, response1.getStatusCode());
        assertEquals(HttpStatus.ACCEPTED, response2.getStatusCode());
        assertEquals(body1, body2);
        assertEquals(eventId1, eventId2);
    }

    @Test
    void testDifferentIdempotencyKeysCreateDifferentInvoices() {
        // Arrange
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();

        HttpHeaders headers1 = new HttpHeaders();
        headers1.set("Idempotency-Key", key1);
        headers1.set("Content-Type", "application/json");
        HttpEntity<Invoice> request1 = new HttpEntity<>(testInvoice, headers1);

        HttpHeaders headers2 = new HttpHeaders();
        headers2.set("Idempotency-Key", key2);
        headers2.set("Content-Type", "application/json");
        HttpEntity<Invoice> request2 = new HttpEntity<>(testInvoice, headers2);

        // Act
        ResponseEntity<String> response1 = restTemplate.postForEntity("/invoices", request1, String.class);
        ResponseEntity<String> response2 = restTemplate.postForEntity("/invoices", request2, String.class);

        String eventId1 = extractEventId(response1.getBody());
        String eventId2 = extractEventId(response2.getBody());

        // Assert
        assertEquals(HttpStatus.ACCEPTED, response1.getStatusCode());
        assertEquals(HttpStatus.ACCEPTED, response2.getStatusCode());
        assertNotEquals(eventId1, eventId2);
    }

    @Test
    void testMissingIdempotencyKeyReturnsError() {
        // Arrange
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<Invoice> request = new HttpEntity<>(testInvoice, headers);

        // Act
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/invoices",
                request,
                String.class
        );

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("MISSING_IDEMPOTENCY_KEY") ||
                response.getBody().contains("Idempotency-Key"));
    }

    @Test
    void testGetAllInvoices() {
        // Arrange
        Invoice invoice1 = new Invoice();
        invoice1.setId(UUID.randomUUID().toString());
        invoice1.setVendorName("Vendor1");
        invoice1.setAmount(new BigDecimal("100.00"));
        invoice1.setCategory("INFRASTRUCTURE");

        Invoice invoice2 = new Invoice();
        invoice2.setId(UUID.randomUUID().toString());
        invoice2.setVendorName("Vendor2");
        invoice2.setAmount(new BigDecimal("200.00"));
        invoice2.setCategory("SOFTWARE");

        invoiceRepository.save(invoice1);
        invoiceRepository.save(invoice2);

        // Act
        ResponseEntity<List> response = restTemplate.getForEntity("/invoices", List.class);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void testGetInvoiceById() {
        // Arrange
        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID().toString());
        invoice.setVendorName("TestVendor");
        invoice.setAmount(new BigDecimal("500.00"));
        invoice.setCategory("INFRASTRUCTURE");
        invoiceRepository.save(invoice);

        // Act
        ResponseEntity<Invoice> response = restTemplate.getForEntity(
                "/invoices/" + invoice.getId(),
                Invoice.class
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(invoice.getId(), response.getBody().getId());
        assertEquals("TestVendor", response.getBody().getVendorName());
    }

    @Test
    void testGetNonExistentInvoiceReturns404() {
        // Act
        ResponseEntity<Invoice> response = restTemplate.getForEntity(
                "/invoices/non-existent-id",
                Invoice.class
        );

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testGetIdempotencyStatus() {
        // Arrange
        IdempotencyKey record = new IdempotencyKey(
                idempotencyKey,
                UUID.randomUUID().toString(),
                "Invoice accepted"
        );
        idempotencyRepository.save(record);

        // Act
        ResponseEntity<IdempotencyKey> response = restTemplate.getForEntity(
                "/invoices/idempotency/" + idempotencyKey,
                IdempotencyKey.class
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(idempotencyKey, response.getBody().getClientProvidedKey());
    }

    @Test
    void testGetInvoiceRequestHistory() {
        // Arrange
        String invoiceId = UUID.randomUUID().toString();

        IdempotencyKey record1 = new IdempotencyKey(UUID.randomUUID().toString(), invoiceId, "Response1");
        IdempotencyKey record2 = new IdempotencyKey(UUID.randomUUID().toString(), invoiceId, "Response2");

        idempotencyRepository.save(record1);
        idempotencyRepository.save(record2);

        // Act
        ResponseEntity<List> response = restTemplate.getForEntity(
                "/invoices/history/" + invoiceId,
                List.class
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void testIdempotencyKeyValidity() {
        // Arrange
        IdempotencyKey validKey = new IdempotencyKey(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "Response"
        );

        IdempotencyKey expiredKey = new IdempotencyKey(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "Response"
        );
        expiredKey.setExpiresAt(Instant.now().minusSeconds(3600));

        // Assert
        assertTrue(validKey.isValid());
        assertFalse(expiredKey.isValid());
    }

    @Test
    void testInvoiceRepositoryOperations() {
        // Arrange
        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID().toString());
        invoice.setVendorName("TestCorp");
        invoice.setAmount(new BigDecimal("1000.00"));
        invoice.setCategory("INFRASTRUCTURE");
        invoice.setStatus(Invoice.Status.PENDING);

        // Act - Save
        invoiceRepository.save(invoice);

        // Assert - Find by ID
        Invoice found = invoiceRepository.findById(invoice.getId()).orElse(null);
        assertNotNull(found);
        assertEquals("TestCorp", found.getVendorName());

        // Act - Find by Status
        List<Invoice> pending = invoiceRepository.findByStatus(Invoice.Status.PENDING);
        assertTrue(pending.stream().anyMatch(inv -> inv.getId().equals(invoice.getId())));
    }

    @Test
    void testIdempotencyRepositoryOperations() {
        // Arrange
        IdempotencyKey record = new IdempotencyKey(
                idempotencyKey,
                UUID.randomUUID().toString(),
                "Invoice accepted"
        );

        // Act - Save
        idempotencyRepository.save(record);

        // Assert - Find by key
        IdempotencyKey found = idempotencyRepository.findByClientProvidedKey(idempotencyKey).orElse(null);
        assertNotNull(found);
        assertEquals(idempotencyKey, found.getClientProvidedKey());

        // Assert - Find by status
        List<IdempotencyKey> completed = idempotencyRepository.findByResponseStatus("COMPLETED");
        assertTrue(completed.stream().anyMatch(r -> r.getClientProvidedKey().equals(idempotencyKey)));
    }

    @Test
    void testMetricsEndpoint() {
        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/actuator/prometheus",
                String.class
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().length() > 0);
    }

    @Test
    void testActuatorEndpoints() {
        // Test health endpoint
        ResponseEntity<String> health = restTemplate.getForEntity(
                "/actuator/health",
                String.class
        );
        assertEquals(HttpStatus.OK, health.getStatusCode());

        // Test metrics endpoint
        ResponseEntity<String> metrics = restTemplate.getForEntity(
                "/actuator/metrics",
                String.class
        );
        assertEquals(HttpStatus.OK, metrics.getStatusCode());
    }

    // Helper method to extract Event ID from response
    private String extractEventId(String response) {
        if (response == null) return null;
        String[] parts = response.split("Event ID: ");
        if (parts.length < 2) return null;
        return parts[1].trim();
    }
}
