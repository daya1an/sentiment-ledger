package com.daya.project.sentiment_ledger.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PolicyRetrievalService {

    private final VectorStore vectorStore;

    @Value("${sentiment-ledger.search.top-k:2}")
    private int topK;

    public PolicyRetrievalService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Searches MongoDB Atlas for policies matching the invoice category.
     * Will be exposed to Gemini as a Tool.
     */
    public String getPolicyContext(String category) {
        // Input validation
        if (category == null || category.trim().isEmpty()) {
            log.warn("⚠️ Empty category provided for policy retrieval");
            return "No category specified. Cannot retrieve policies.";
        }

        log.info("🔍 AI requested policy context for category: {}", category);

        try {
            // Search MongoDB for top K most relevant policy chunks
            List<Document> relevantChunks = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(category)
                            .topK(topK)
                            .build()
            );

            if (relevantChunks.isEmpty()) {
                log.warn("⚠️ No specific policies found for category: {}", category);
                return "No specific policy found. Default to manual review.";
            }

            // Combine retrieved text chunks
            String policyText = relevantChunks.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n"));

            log.info("✅ Retrieved {} relevant policy rules for AI evaluation.", relevantChunks.size());
            return policyText;

        } catch (Exception e) {
            log.error("❌ Error retrieving policy context for category: {}", category, e);
            return "Error retrieving policies. Please retry or escalate for manual review.";
        }
    }
}