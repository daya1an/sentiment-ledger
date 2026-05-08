package com.daya.project.sentiment_ledger.controller;

import com.daya.project.sentiment_ledger.service.policy.PolicyRetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@Slf4j
public class AdminController {

    private final PolicyRetrievalService policyRetrievalService;

    public AdminController(PolicyRetrievalService policyRetrievalService) {
        this.policyRetrievalService = policyRetrievalService;
    }

    @PostMapping("/refresh-policies-cache")
    public ResponseEntity<String> refreshPoliciesCache() {
        policyRetrievalService.refreshPoliciesCache();
        log.info("✅ Policies cache refreshed");
        return ResponseEntity.ok("Policies cache refreshed successfully");
    }
}
