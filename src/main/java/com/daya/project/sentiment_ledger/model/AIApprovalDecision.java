package com.daya.project.sentiment_ledger.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AIApprovalDecision {
    private String decision; // APPROVED, REJECTED, MANUAL_REVIEW
    private double confidence; // 0.0-1.0
    private String reasoning; // Explanation
    private List<String> riskFlags; // Suspicious patterns
    private String requiresApprovalLevel; // NONE, MANAGER, DIRECTOR, CFO

    public boolean isValid() {
        return decision != null &&
                confidence >= 0.0 && confidence <= 1.0 &&
                reasoning != null && !reasoning.isBlank() &&
                riskFlags != null &&
                requiresApprovalLevel != null;
    }

    public String getMainDecision() {
        return decision.toUpperCase();
    }
}
