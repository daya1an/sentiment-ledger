package com.daya.project.sentiment_ledger.service;

import com.daya.project.sentiment_ledger.model.AIApprovalDecision;
import com.daya.project.sentiment_ledger.model.Invoice;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AIDecisionService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private ThreadLocal<String> reasoningContext = new ThreadLocal<>();

    public AIDecisionService(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.objectMapper = new ObjectMapper();
    }

    public AIApprovalDecision getApprovalDecision(Invoice invoice, String policies) {
        String prompt = String.format("""
            You are an expert financial auditor. Review the invoice against the policies and respond ONLY with valid JSON.
            
            POLICIES:
            %s
            
            INVOICE DETAILS:
            - Vendor: %s
            - Amount: ₹%s
            - Category: %s
            
            RESPOND WITH THIS EXACT JSON FORMAT (no markdown, no explanation):
            {
              "decision": "APPROVED" | "REJECTED" | "MANUAL_REVIEW",
              "confidence": 0.0-1.0,
              "reasoning": "Brief explanation (max 150 chars)",
              "riskFlags": ["flag1", "flag2"],
              "requiresApprovalLevel": "NONE" | "MANAGER" | "DIRECTOR" | "CFO"
            }
            
            Requirements:
            - Be decisive. If policies are clear, commit to a decision.
            - confidence: 0.9+ for certain decisions, 0.5-0.7 for uncertain, <0.5 needs MANUAL_REVIEW
            - riskFlags: Note any suspicious patterns (duplicate vendor, unusual amount, policy edge cases)
            - requiresApprovalLevel: Who in the org should sign off on this decision?
            """, policies, invoice.getVendorName(), invoice.getAmount(), invoice.getCategory());

        log.info("🤖 Calling Gemini AI for invoice: {} | Vendor: {}",
                invoice.getId(), invoice.getVendorName());

        try {
            String aiResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .trim();

            aiResponse = stripJsonFences(aiResponse);

            log.debug("Raw AI response: {}", aiResponse);

            // Parse JSON response
            AIApprovalDecision decision = objectMapper.readValue(aiResponse, AIApprovalDecision.class);

            // Validate decision
            if (!decision.isValid()) {
                log.warn("⚠️ Invalid AI decision structure. Defaulting to MANUAL_REVIEW");
                decision.setDecision("MANUAL_REVIEW");
                decision.setConfidence(0.0);
                decision.setReasoning("AI response validation failed");
            }

            // Store reasoning for audit
            String fullContext = String.format(
                    "Decision: %s | Confidence: %.2f | Reasoning: %s | Risk Flags: %s | Approval Level: %s",
                    decision.getDecision(),
                    decision.getConfidence(),
                    decision.getReasoning(),
                    String.join(", ", decision.getRiskFlags()),
                    decision.getRequiresApprovalLevel()
            );
            reasoningContext.set(fullContext);

            log.info("🎯 AI Decision: {} (confidence: %.2f) | Flags: {}",
                    decision.getDecision(), decision.getConfidence(), decision.getRiskFlags());

            return decision;

        } catch (JsonProcessingException e) {
            log.error("❌ Failed to parse AI response as JSON: {}", e.getMessage());
            reasoningContext.set("JSON parsing failed: " + e.getMessage());

            // Fail safe: return MANUAL_REVIEW
            AIApprovalDecision fallback = new AIApprovalDecision();
            fallback.setDecision("MANUAL_REVIEW");
            fallback.setConfidence(0.0);
            fallback.setReasoning("AI service error - defaulting to manual review");
            fallback.setRiskFlags(List.of("AI_SERVICE_ERROR"));
            fallback.setRequiresApprovalLevel("MANAGER");

            return fallback;
        }
    }

    public String getReasoningContext() {
        return Optional.ofNullable(reasoningContext.get())
                .orElse("No reasoning available");
    }
    private String stripJsonFences(String response) {
        if (response.startsWith("```")) {
            response = response.replaceFirst("^```(json)?", "").trim();
            if (response.endsWith("```")) {
                response = response.substring(0, response.length() - 3).trim();
            }
        }
        return response;
    }

    public void clearReasoningContext() {
        reasoningContext.remove();
    }
}