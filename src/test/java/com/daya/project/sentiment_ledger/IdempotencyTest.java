package com.daya.project.sentiment_ledger;

import com.daya.project.sentiment_ledger.controller.InvoiceController;
import com.daya.project.sentiment_ledger.model.Invoice;
import com.daya.project.sentiment_ledger.model.IdempotencyKey;
import com.daya.project.sentiment_ledger.repository.InvoiceRepository;
import com.daya.project.sentiment_ledger.repository.IdempotencyRepository;
import com.daya.project.sentiment_ledger.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class IdempotencyTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private IdempotencyRepository idempotencyRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private InvoiceController invoiceController;

    private Invoice testInvoice;
    private String testIdempotencyKey;

    @BeforeEach
    void setUp() {
        testInvoice = new Invoice();
        testInvoice.setVendorName("TechCorp");
        testInvoice.setAmount(new BigDecimal("1500.00"));
        testInvoice.setCategory("INFRASTRUCTURE");

        testIdempotencyKey = UUID.randomUUID().toString();
    }

    /**
     * Test: First request with idempotency key should be processed
     */
    @Test
    void testFirstRequestProcessesNormally() {
        // Arrange
        when(idempotencyRepository.findByClientProvidedKey(testIdempotencyKey))
                .thenReturn(Optional.empty()); // No previous request

        // Act
        ResponseEntity<String> response = invoiceController.submitInvoice(testInvoice, testIdempotencyKey);

        // Assert
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertTrue(response.getBody().contains("Event ID"));

        // Verify idempotency record was saved
        verify(idempotencyRepository).save(argThat(record ->
                record.getClientProvidedKey().equals(testIdempotencyKey)
        ));

        // Verify Kafka was called
        verify(kafkaTemplate).send(anyString(), anyString(), any(Invoice.class));
    }

    /**
     * Test: Retry with same idempotency key returns cached response
     */
    @Test
    void testRetryWithSameKeyReturnsCachedResponse() {
        // Arrange
        String invoiceId = UUID.randomUUID().toString();
        String expectedResponse = "Invoice accepted into the processing queue. Event ID: " + invoiceId;

        IdempotencyKey existingRecord = new IdempotencyKey();
        existingRecord.setClientProvidedKey(testIdempotencyKey);
        existingRecord.setInvoiceId(invoiceId);
        existingRecord.setResponseStatus("COMPLETED");
        existingRecord.setResponseBody(expectedResponse);
        existingRecord.setCreatedAt(Instant.now());
        existingRecord.setExpiresAt(Instant.now().plus(Duration.ofHours(24)));

        when(idempotencyRepository.findByClientProvidedKey(testIdempotencyKey))
                .thenReturn(Optional.of(existingRecord));

        // Act
        ResponseEntity<String> response = invoiceController.submitInvoice(testInvoice, testIdempotencyKey);

        // Assert
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals(expectedResponse, response.getBody());

        // Verify Kafka was NOT called (we returned cached response)
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());

        // Verify new idempotency record was NOT saved
        verify(idempotencyRepository, never()).save(any());
    }

    /**
     * Test: Expired idempotency key is treated as new request
     */
    @Test
    void testExpiredIdempotencyKeyTreatsAsNewRequest() {
        // Arrange - Key that expired yesterday
        IdempotencyKey expiredRecord = new IdempotencyKey();
        expiredRecord.setClientProvidedKey(testIdempotencyKey);
        expiredRecord.setExpiresAt(Instant.now().minus(Duration.ofHours(1)));

        when(idempotencyRepository.findByClientProvidedKey(testIdempotencyKey))
                .thenReturn(Optional.of(expiredRecord));

        // Act
        ResponseEntity<String> response = invoiceController.submitInvoice(testInvoice, testIdempotencyKey);

        // Assert
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());

        // Verify Kafka was called (treated as new request)
        verify(kafkaTemplate).send(anyString(), anyString(), any(Invoice.class));

        // Verify new idempotency record was saved
        verify(idempotencyRepository).save(any(IdempotencyKey.class));
    }

    /**
     * Test: Multiple retries always return same response
     */
    @Test
    void testMultipleRetriesReturnSameResponse() {
        // Arrange
        String invoiceId = UUID.randomUUID().toString();
        String expectedResponse = "Invoice accepted into the processing queue. Event ID: " + invoiceId;

        IdempotencyKey existingRecord = new IdempotencyKey(testIdempotencyKey, invoiceId, expectedResponse);

        when(idempotencyRepository.findByClientProvidedKey(testIdempotencyKey))
                .thenReturn(Optional.of(existingRecord));

        // Act - Simulate 3 retries
        ResponseEntity<String> response1 = invoiceController.submitInvoice(testInvoice, testIdempotencyKey);
        ResponseEntity<String> response2 = invoiceController.submitInvoice(testInvoice, testIdempotencyKey);
        ResponseEntity<String> response3 = invoiceController.submitInvoice(testInvoice, testIdempotencyKey);

        // Assert - All responses are identical
        assertEquals(response1.getBody(), response2.getBody());
        assertEquals(response2.getBody(), response3.getBody());
        assertEquals(HttpStatus.ACCEPTED, response1.getStatusCode());
        assertEquals(HttpStatus.ACCEPTED, response2.getStatusCode());
        assertEquals(HttpStatus.ACCEPTED, response3.getStatusCode());

        // Verify Kafka was never called
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    /**
     * Test: Different idempotency keys result in different invoices
     */
    @Test
    void testDifferentKeysCreateDifferentInvoices() {
        // Arrange
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();

        when(idempotencyRepository.findByClientProvidedKey(key1))
                .thenReturn(Optional.empty());
        when(idempotencyRepository.findByClientProvidedKey(key2))
                .thenReturn(Optional.empty());

        // Act
        ResponseEntity<String> response1 = invoiceController.submitInvoice(testInvoice, key1);
        ResponseEntity<String> response2 = invoiceController.submitInvoice(testInvoice, key2);

        // Assert
        assertEquals(HttpStatus.ACCEPTED, response1.getStatusCode());
        assertEquals(HttpStatus.ACCEPTED, response2.getStatusCode());

        // Responses should be different (different event IDs)
        assertNotEquals(response1.getBody(), response2.getBody());

        // Verify Kafka was called twice with different invoice IDs
        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), any(Invoice.class));
    }

    /**
     * Test: IdempotencyKey validity check
     */
    @Test
    void testIdempotencyKeyValidityCheck() {
        // Arrange - Create a valid and expired key
        IdempotencyKey validKey = new IdempotencyKey(testIdempotencyKey, "inv-1", "response");
        validKey.setExpiresAt(Instant.now().plus(Duration.ofHours(1))); // Expires in 1 hour

        IdempotencyKey expiredKey = new IdempotencyKey(testIdempotencyKey, "inv-2", "response");
        expiredKey.setExpiresAt(Instant.now().minus(Duration.ofHours(1))); // Expired 1 hour ago

        // Assert
        assertTrue(validKey.isValid(), "Valid key should pass isValid() check");
        assertFalse(expiredKey.isValid(), "Expired key should fail isValid() check");
    }
}
