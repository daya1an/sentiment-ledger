package com.daya.project.sentiment_ledger;

import com.daya.project.sentiment_ledger.config.kafka.KafkaTopicConfig;
import com.daya.project.sentiment_ledger.model.AIApprovalDecision;
import com.daya.project.sentiment_ledger.model.Invoice;
import com.daya.project.sentiment_ledger.model.LedgerEntry;
import com.daya.project.sentiment_ledger.model.PaymentEvent;
import com.daya.project.sentiment_ledger.repository.InvoiceRepository;
import com.daya.project.sentiment_ledger.repository.LedgerEntryRepository;
import com.daya.project.sentiment_ledger.service.AIDecisionService;
import com.daya.project.sentiment_ledger.service.MetricsService;
import com.daya.project.sentiment_ledger.service.invoice.InvoiceProcessor;
import com.daya.project.sentiment_ledger.service.policy.PolicyRetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InvoiceProcessorTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private PolicyRetrievalService policyRetrievalService;

    @Mock
    private AIDecisionService aiDecisionService;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private MetricsService metricsService;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private InvoiceProcessor invoiceProcessor;

    private Invoice testInvoice;
    private String testInvoiceId;

    @BeforeEach
    void setUp() {
        testInvoiceId = UUID.randomUUID().toString();

        testInvoice = new Invoice();
        testInvoice.setId(testInvoiceId);
        testInvoice.setVendorName("TechCorp");
        testInvoice.setAmount(new BigDecimal("1500.00"));
        testInvoice.setCategory("INFRASTRUCTURE");

        // Setup Redis mock
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testProcessInvoice_SuccessfulApproval() {
        // Arrange
        when(valueOperations.setIfAbsent(
                "invoice:lock:" + testInvoiceId,
                anyString(),
                any(Duration.class)
        )).thenReturn(true);

        when(policyRetrievalService.getPolicyContext("INFRASTRUCTURE"))
                .thenReturn("Cloud infrastructure under 5000 is auto-approved");

        when(aiDecisionService.getApprovalDecision(testInvoice, "Cloud infrastructure under 5000 is auto-approved"))
                .thenReturn(new AIApprovalDecision("APPROVED", 0.95, "Approved by policy", List.of(), "NONE"));

        when(aiDecisionService.getReasoningContext())
                .thenReturn("AI approved based on infrastructure policy");

        // Act
        invoiceProcessor.processInvoice(testInvoice);

        // Assert
        verify(invoiceRepository).save(argThat(invoice ->
                invoice.getStatus() == Invoice.Status.AI_APPROVED
        ));
        verify(ledgerEntryRepository).save(any(LedgerEntry.class));
        verify(kafkaTemplate).send(
                eq(KafkaTopicConfig.PAYMENT_EXECUTED_TOPIC),
                eq(testInvoiceId),
                any(PaymentEvent.class)
        );
        verify(metricsService).recordInvoiceProcessingLatency(anyLong());
        verify(metricsService).recordKafkaMessageProcessed("invoice-submitted");
        verify(redisTemplate).delete("invoice:lock:" + testInvoiceId);
    }

    @Test
    void testProcessInvoice_SuccessfulRejection() {
        // Arrange
        when(valueOperations.setIfAbsent(
                "invoice:lock:" + testInvoiceId,
                anyString(),
                any(Duration.class)
        )).thenReturn(true);

        when(policyRetrievalService.getPolicyContext("INFRASTRUCTURE"))
                .thenReturn("Cloud infrastructure policy");

        when(aiDecisionService.getApprovalDecision(testInvoice, "Cloud infrastructure policy"))
                .thenReturn(new AIApprovalDecision("Rejected", 0.95, "Rejected by policy", List.of(), "NONE"));

        when(aiDecisionService.getReasoningContext())
                .thenReturn("AI rejected based on amount exceeding limit");

        // Act
        invoiceProcessor.processInvoice(testInvoice);

        // Assert
        verify(invoiceRepository).save(argThat(invoice ->
                invoice.getStatus() == Invoice.Status.REJECTED
        ));
        verify(ledgerEntryRepository).save(any(LedgerEntry.class));
        verify(kafkaTemplate).send(
                eq(KafkaTopicConfig.PAYMENT_EXECUTED_TOPIC),
                eq(testInvoiceId),
                any(PaymentEvent.class)
        );
    }

//    @Test
//    void testProcessInvoice_ManualReviewRequired() {
//        // Arrange
//        when(valueOperations.setIfAbsent(
//                "invoice:lock:" + testInvoiceId,
//                anyString(),
//                any(Duration.class)
//        )).thenReturn(true);
//
//        when(policyRetrievalService.getPolicyContext("INFRASTRUCTURE"))
//                .thenReturn("Infrastructure policies");
//
//        when(aiDecisionService.getApprovalDecision(testInvoice, "Infrastructure policies"))
//                .thenReturn(new AIApprovalDecision("APPROVED", 0.95, "Approved by policy", List.of(), "NONE"))
//
//        when(aiDecisionService.getReasoningContext())
//                .thenReturn("Low confidence, requires manual review");
//
//        // Act
//        invoiceProcessor.processInvoice(testInvoice);
//
//        // Assert
//        verify(invoiceRepository).save(argThat(invoice ->
//                invoice.getStatus() == Invoice.Status.PENDING
//        ));
//        verify(ledgerEntryRepository).save(any(LedgerEntry.class));
//        verify(kafkaTemplate).send(
//                eq(KafkaTopicConfig.PAYMENT_EXECUTED_TOPIC),
//                eq(testInvoiceId),
//                any(PaymentEvent.class)
//        );
//    }

    @Test
    void testProcessInvoice_DuplicateDetected() {
        // Arrange - Lock already held (duplicate)
        when(valueOperations.setIfAbsent(
                "invoice:lock:" + testInvoiceId,
                anyString(),
                any(Duration.class)
        )).thenReturn(false);

        // Act
        invoiceProcessor.processInvoice(testInvoice);

        // Assert - Should not process
        verify(policyRetrievalService, never()).getPolicyContext(anyString());
        verify(aiDecisionService, never()).getApprovalDecision(any(), anyString());
        verify(invoiceRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
        verify(metricsService).incrementDuplicateInvoicesDetected();
    }

    @Test
    void testProcessInvoice_LockAcquisitionAndRelease() {
        // Arrange
        String lockKey = "invoice:lock:" + testInvoiceId;
        String lockValue = UUID.randomUUID().toString();

        when(valueOperations.setIfAbsent(lockKey, anyString(), any(Duration.class)))
                .thenReturn(true);

        when(valueOperations.get(lockKey))
                .thenReturn(lockValue);

        when(policyRetrievalService.getPolicyContext(anyString()))
                .thenReturn("Policies");

        when(aiDecisionService.getApprovalDecision(any(), anyString()))
                .thenReturn(new AIApprovalDecision("APPROVED", 0.95, "Approved by policy", List.of(), "NONE"));

        when(aiDecisionService.getReasoningContext())
                .thenReturn("Reasoning");

        // Act
        invoiceProcessor.processInvoice(testInvoice);

        // Assert - Lock was acquired
        verify(valueOperations).setIfAbsent(eq(lockKey), anyString(), any(Duration.class));

        // Assert - Lock was released
        verify(redisTemplate).delete(lockKey);
    }

    @Test
    void testProcessInvoice_HandleException() {
        // Arrange
        when(valueOperations.setIfAbsent(
                "invoice:lock:" + testInvoiceId,
                anyString(),
                any(Duration.class)
        )).thenReturn(true);

        when(policyRetrievalService.getPolicyContext(anyString()))
                .thenThrow(new RuntimeException("Policy service error"));

        when(valueOperations.get("invoice:lock:" + testInvoiceId))
                .thenReturn("some-value");

        // Act & Assert - Should throw exception
        assertThrows(RuntimeException.class, () -> invoiceProcessor.processInvoice(testInvoice));

        // Verify invoice was marked as PENDING for retry
        verify(invoiceRepository).save(argThat(invoice ->
                invoice.getStatus() == Invoice.Status.PENDING
        ));

        // Verify error metrics recorded
        verify(metricsService).incrementProcessingErrors();

        // Verify lock was released even on error
        verify(redisTemplate).delete("invoice:lock:" + testInvoiceId);
    }

    @Test
    void testProcessInvoice_PolicyRetrievalWithCategory() {
        // Arrange
        testInvoice.setCategory("TRAVEL");

        when(valueOperations.setIfAbsent(
                "invoice:lock:" + testInvoiceId,
                anyString(),
                any(Duration.class)
        )).thenReturn(true);

        when(policyRetrievalService.getPolicyContext("TRAVEL"))
                .thenReturn("Travel expenses require manual review");

        when(aiDecisionService.getApprovalDecision(testInvoice, "Travel expenses require manual review"))
                .thenReturn(new AIApprovalDecision("MANUAL REVIEW", 0.95, "Manual Review by policy", List.of(), "NONE"));

        when(aiDecisionService.getReasoningContext())
                .thenReturn("Travel policy requires manual review");

        // Act
        invoiceProcessor.processInvoice(testInvoice);

        // Assert
        verify(policyRetrievalService).getPolicyContext("TRAVEL");
    }

    @Test
    void testProcessInvoice_KafkaPaymentEventPublished() {
        // Arrange
        when(valueOperations.setIfAbsent(
                "invoice:lock:" + testInvoiceId,
                anyString(),
                any(Duration.class)
        )).thenReturn(true);

        when(policyRetrievalService.getPolicyContext(anyString()))
                .thenReturn("Policies");

        when(aiDecisionService.getApprovalDecision(testInvoice, "Policies"))
                .thenReturn(new AIApprovalDecision("APPROVED", 0.95, "Approved by policy", List.of(), "NONE"));

        when(aiDecisionService.getReasoningContext())
                .thenReturn("Approved");

        // Act
        invoiceProcessor.processInvoice(testInvoice);

        // Assert - Kafka message published
        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(kafkaTemplate).send(
                eq(KafkaTopicConfig.PAYMENT_EXECUTED_TOPIC),
                eq(testInvoiceId),
                eventCaptor.capture()
        );

        PaymentEvent event = eventCaptor.getValue();
        assertEquals(testInvoiceId, event.getInvoiceId());
        assertEquals(new BigDecimal("1500.00"), event.getAmount());
        assertEquals("APPROVED", event.getDecision());
    }

    @Test
    void testProcessInvoice_LedgerEntryCreated() {
        // Arrange
        when(valueOperations.setIfAbsent(
                "invoice:lock:" + testInvoiceId,
                anyString(),
                any(Duration.class)
        )).thenReturn(true);

        when(policyRetrievalService.getPolicyContext(anyString()))
                .thenReturn("Policies");

        when(aiDecisionService.getApprovalDecision(testInvoice, "Policies"))
                .thenReturn(new AIApprovalDecision("APPROVED", 0.95, "Approved by policy", List.of(), "NONE"));

        String reasoning = "AI approved with 0.95 confidence";
        when(aiDecisionService.getReasoningContext())
                .thenReturn(reasoning);

        // Act
        invoiceProcessor.processInvoice(testInvoice);

        // Assert
        ArgumentCaptor<LedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(ledgerCaptor.capture());

        LedgerEntry entry = ledgerCaptor.getValue();
        assertEquals(testInvoiceId, entry.getInvoiceId());
        assertEquals("APPROVED", entry.getActionTaken());
        assertEquals(reasoning, entry.getAiReasoningContext());
    }

    @Test
    void testProcessInvoice_InvoiceStatusUpdated() {
        // Arrange
        when(valueOperations.setIfAbsent(
                "invoice:lock:" + testInvoiceId,
                anyString(),
                any(Duration.class)
        )).thenReturn(true);

        when(policyRetrievalService.getPolicyContext(anyString()))
                .thenReturn("Policies");

        when(aiDecisionService.getApprovalDecision(testInvoice, "Policies"))
                .thenReturn(new AIApprovalDecision("APPROVED", 0.95, "Approved by policy", List.of(), "NONE"));

        when(aiDecisionService.getReasoningContext())
                .thenReturn("Reasoning");

        // Act
        invoiceProcessor.processInvoice(testInvoice);

        // Assert
        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(invoiceCaptor.capture());

        Invoice saved = invoiceCaptor.getValue();
        assertEquals(Invoice.Status.AI_APPROVED, saved.getStatus());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void testProcessInvoice_MetricsRecorded() {
        // Arrange
        when(valueOperations.setIfAbsent(
                "invoice:lock:" + testInvoiceId,
                anyString(),
                any(Duration.class)
        )).thenReturn(true);

        when(policyRetrievalService.getPolicyContext(anyString()))
                .thenReturn("Policies");

        when(aiDecisionService.getApprovalDecision(testInvoice, "Policies"))
                .thenReturn(new AIApprovalDecision("APPROVED", 0.95, "Approved by policy", List.of(), "NONE"));

        when(aiDecisionService.getReasoningContext())
                .thenReturn("Reasoning");

        // Act
        invoiceProcessor.processInvoice(testInvoice);

        // Assert
        verify(metricsService).recordInvoiceProcessingLatency(anyLong());
        verify(metricsService).recordKafkaMessageProcessed("invoice-submitted");
    }

    @Test
    void testProcessInvoice_UnknownDecisionDefaultsToPending() {
        // Arrange
        when(valueOperations.setIfAbsent(
                "invoice:lock:" + testInvoiceId,
                anyString(),
                any(Duration.class)
        )).thenReturn(true);

        when(policyRetrievalService.getPolicyContext(anyString()))
                .thenReturn("Policies");

        when(aiDecisionService.getApprovalDecision(testInvoice, "Policies"))
                .thenReturn(new AIApprovalDecision("UNKNOWN_DECISION", 0.95, "UNKNOWN_DECISION by policy", List.of(), "NONE"));

        when(aiDecisionService.getReasoningContext())
                .thenReturn("Reasoning");

        // Act
        invoiceProcessor.processInvoice(testInvoice);

        // Assert - Should default to PENDING
        verify(invoiceRepository).save(argThat(invoice ->
                invoice.getStatus() == Invoice.Status.PENDING
        ));
    }

    @Test
    void testProcessInvoice_LockValueMismatchPreventsRelease() {
        // Arrange
        String lockKey = "invoice:lock:" + testInvoiceId;
        String acquiredValue = UUID.randomUUID().toString();
        String storedValue = UUID.randomUUID().toString();

        when(valueOperations.setIfAbsent(lockKey, anyString(), any(Duration.class)))
                .thenReturn(true);

        when(valueOperations.get(lockKey))
                .thenReturn(storedValue); // Different value

        when(policyRetrievalService.getPolicyContext(anyString()))
                .thenReturn("Policies");

        when(aiDecisionService.getApprovalDecision(any(), anyString()))
                .thenReturn(new AIApprovalDecision("APPROVED", 0.95, "Approved by policy", List.of(), "NONE"));

        when(aiDecisionService.getReasoningContext())
                .thenReturn("Reasoning");

        // Act
        invoiceProcessor.processInvoice(testInvoice);

        // Assert - Lock should not be deleted (value mismatch)
        verify(redisTemplate, never()).delete(lockKey);
    }

    @Test
    void testProcessInvoice_ClearsAIReasoningContext() {
        // Arrange
        when(valueOperations.setIfAbsent(
                "invoice:lock:" + testInvoiceId,
                anyString(),
                any(Duration.class)
        )).thenReturn(true);

        when(policyRetrievalService.getPolicyContext(anyString()))
                .thenReturn("Policies");

        when(aiDecisionService.getApprovalDecision(testInvoice, "Policies"))
                .thenReturn(new AIApprovalDecision("APPROVED", 0.95, "Approved by policy", List.of(), "NONE"));

        when(aiDecisionService.getReasoningContext())
                .thenReturn("Reasoning");

        // Act
        invoiceProcessor.processInvoice(testInvoice);

        // Assert - Reasoning context cleared
        verify(aiDecisionService).clearReasoningContext();
    }
}
