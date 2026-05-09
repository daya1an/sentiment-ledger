package com.daya.project.sentiment_ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AIDecisionStats {
    private long totalInvoices;
    private long approved;
    private long rejected;
    private long manualReview;
    private double approvalRate;
    private double averageConfidence;
}