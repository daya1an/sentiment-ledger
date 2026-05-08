package com.daya.project.sentiment_ledger.service;

import com.daya.project.sentiment_ledger.model.Invoice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Pattern;

import static java.util.Optional.ofNullable;

@Slf4j
@Service
public class AIDecisionService {
    private final ChatClient chatClient;
    private final ThreadLocal<String> reasoningContext = new ThreadLocal<>();

    // Pre-compiled regex patterns to avoid recompilation
    private static final Pattern MARKDOWN_FENCE_PATTERN = Pattern.compile("```(?:json)?");

    public AIDecisionService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String getApprovalDecision(Invoice invoice, String policies) {
        String prompt = """
                        Review invoice against policies. Return ONLY JSON:
                        {
                          "decision": "APPROVED|REJECTED|MANUAL_REVIEW",
                          "reasoning": "Brief explanation (max 200 chars)",
                          "confidence": 0.0-1.0
                        }
                        
                        Policies:
                        """+ policies +"""
                        Invoice: Vendor=%s | Amount=%s | Category=%s
                        """.formatted(invoice.getVendorName(), invoice.getAmount(), invoice.getCategory());

        log.info("🤖 Calling Gemini AI for invoice: {}", invoice.getId());

        String aiResponse = chatClient.prompt()
                .user(prompt)
                .call()
                .content()
                .trim();

        try {
            // 🔧 Strip Markdown code fences if present using pre-compiled pattern
            if (aiResponse.startsWith("```")) {
                aiResponse = MARKDOWN_FENCE_PATTERN.matcher(aiResponse)
                        .replaceAll("")
                        .trim();
            }

            // 🔧 Extra safeguard – extract only JSON object portion
            int start = aiResponse.indexOf("{");
            int end = aiResponse.lastIndexOf("}");
            if (start >= 0 && end >= 0) {
                aiResponse = aiResponse.substring(start, end + 1);
            }

            // Parse JSON response
            ObjectMapper mapper = new ObjectMapper();
            JsonNode responseJson = mapper.readTree(aiResponse);

            String decision = responseJson.get("decision").asText();
            String reasoning = responseJson.get("reasoning").asText();
            double confidence = responseJson.get("confidence").asDouble();

            // Store reasoning in thread-local for later audit trail
            String fullContext = String.format(
                    "Decision: %s | Confidence: %.2f | Reasoning: %s | Policies Applied: %s",
                    decision, confidence, reasoning, policies.substring(0, Math.min(200, policies.length()))
            );
            reasoningContext.set(fullContext);

            log.info("🎯 AI Decision: {} (confidence: {})", decision, confidence);
            return decision;

        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", aiResponse, e);
            reasoningContext.set("Failed to parse AI response: " + aiResponse);
            return "MANUAL_REVIEW";
        }
    }

    public String getReasoningContext() {
        return Optional.ofNullable(reasoningContext.get())
                .orElse("No reasoning available");
    }

    public void clearReasoningContext() {
        reasoningContext.remove();
    }
}