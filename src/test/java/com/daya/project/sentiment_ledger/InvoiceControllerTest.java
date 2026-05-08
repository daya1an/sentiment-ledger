package com.daya.project.sentiment_ledger;

import com.daya.project.sentiment_ledger.controller.InvoiceController;
import com.daya.project.sentiment_ledger.model.Invoice;
import com.daya.project.sentiment_ledger.repository.InvoiceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvoiceController.class)
public class InvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Use the new annotation here
    @MockitoBean
    private InvoiceRepository invoiceRepository;

    @Test
    void shouldSubmitInvoiceSuccessfully() throws Exception {
        // 1. Arrange
        Invoice mockInvoice = new Invoice("TechCorp", new BigDecimal("1500.00"), "HARDWARE");
        mockInvoice.setId("mongo-id-12345");

        Mockito.when(invoiceRepository.save(Mockito.any(Invoice.class))).thenReturn(mockInvoice);

        // 2. Act & Assert
        String jsonPayload = """
                {
                    "vendorName": "TechCorp",
                    "amount": 1500.00,
                    "category": "HARDWARE"
                }
                """;

        mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("mongo-id-12345"))
                .andExpect(jsonPath("$.vendorName").value("TechCorp"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void shouldReturnAllInvoices() throws Exception {
        // 1. Arrange
        Invoice mockInvoice = new Invoice("Google Cloud", new BigDecimal("300.00"), "INFRASTRUCTURE");
        Mockito.when(invoiceRepository.findAll()).thenReturn(Collections.singletonList(mockInvoice));

        // 2. Act & Assert
        mockMvc.perform(get("/api/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vendorName").value("Google Cloud"));
    }
}
