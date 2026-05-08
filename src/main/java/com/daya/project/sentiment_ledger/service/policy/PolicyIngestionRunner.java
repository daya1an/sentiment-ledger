package com.daya.project.sentiment_ledger.service.policy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class PolicyIngestionRunner implements CommandLineRunner {

    private final VectorStore vectorStore;

    @Value("classpath:financial-policies.txt")
    private Resource policyResource;

    public PolicyIngestionRunner(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("🧠 Initializing Brain: Reading financial policies...");

        // 1. Read the text file
        TextReader textReader = new TextReader(policyResource);
        textReader.getCustomMetadata().put("source", "corporate-financial-policy");
        List<Document> documents = textReader.get();

        // 2. Chunk the text into manageable pieces for the AI
        TokenTextSplitter textSplitter = new TokenTextSplitter();
        List<Document> splitDocuments = textSplitter.apply(documents);

        log.info("🔪 Chunked policies into {} distinct segments. Generating embeddings...", splitDocuments.size());

        // 3. Generate embeddings via Gemini and store them in MongoDB Atlas
        vectorStore.add(splitDocuments);

        log.info("✅ SUCCESS: Financial policies are successfully embedded and stored in MongoDB!");
    }
}
