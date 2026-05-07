package com.daya.project.sentiment_ledger.service;

import com.daya.project.sentiment_ledger.model.Invoice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AIDecisionService {
    private final ChatClient chatClient;

    public AIDecisionService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String getApprovalDecision(Invoice invoice, String policies) {
        String prompt = "Review invoice against policies. Return ONLY one word: APPROVED, REJECTED, or MANUAL_REVIEW.\n\n" +
                "Policies:\n" + policies + "\n\n" +
                "Invoice: Vendor=" + invoice.getVendorName() +
                " | Amount=" + invoice.getAmount() +
                " | Category=" + invoice.getCategory();

        log.info("🤖 Calling Gemini AI with prompt");

        String decision = chatClient.prompt()
                .user(prompt)
                .call()
                .content()
                .trim();

        log.info("🎯 AI Response: {}", decision);
        return decision;
    }
}
