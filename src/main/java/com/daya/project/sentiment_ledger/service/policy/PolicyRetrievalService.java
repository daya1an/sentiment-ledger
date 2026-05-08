package com.daya.project.sentiment_ledger.service.policy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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

    @Cacheable(value = "policies", key = "#category", unless = "#result == null")
    public String getPolicyContext(String category) {
        if (category == null || category.trim().isEmpty()) {
            log.warn("⚠️ Empty category provided");
            return "No category specified. Cannot retrieve policies.";
        }

        log.info("🔍 Cache MISS - Retrieving policies for: {}", category);

        try {
            List<Document> relevantChunks = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(category)
                            .topK(topK)
                            .build()
            );

            if (relevantChunks.isEmpty()) {
                return "No specific policy found. Default to manual review.";
            }

            String policyText = relevantChunks.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n"));

            log.info("✅ Retrieved {} policy rules for category: {}", relevantChunks.size(), category);
            return policyText;

        } catch (Exception e) {
            log.error("❌ Error retrieving policies for: {}", category, e);
            return "Error retrieving policies. Please retry or escalate.";
        }
    }

    @CacheEvict(value = "policies", allEntries = true)
    public void refreshPoliciesCache() {
        log.info("🔄 Refreshing policies cache");
    }
}