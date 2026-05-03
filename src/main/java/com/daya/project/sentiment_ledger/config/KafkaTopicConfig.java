package com.daya.project.sentiment_ledger.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String INVOICE_SUBMITTED_TOPIC = "invoice-submitted";
    public static final String PAYMENT_EXECUTED_TOPIC = "payment-executed";

    @Bean
    public NewTopic invoiceSubmittedTopic() {
        return TopicBuilder.name(INVOICE_SUBMITTED_TOPIC)
                .partitions(1) // 3 partitions allow for parallel processing later if needed
                .replicas(1)   // 1 replica since we are running a single local broker
                .build();
    }

    @Bean
    public NewTopic paymentExecutedTopic() {
        return TopicBuilder.name(PAYMENT_EXECUTED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
