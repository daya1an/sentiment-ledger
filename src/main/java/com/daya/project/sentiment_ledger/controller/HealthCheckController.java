package com.daya.project.sentiment_ledger.controller;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
@Slf4j
public class HealthCheckController {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public HealthCheckController(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @GetMapping("/payment-service")
    public ResponseEntity<Map<String, Object>> paymentServiceHealth() {
        CircuitBreaker cb = circuitBreakerRegistry.find("payment-service")
                .orElseThrow(() -> new RuntimeException("CB not found"));

        Map<String, Object> health = new HashMap<>();
        health.put("service", "payment");
        health.put("status", cb.getState().toString());
        health.put("failure_rate", cb.getMetrics().getFailureRate());
        health.put("slow_call_rate", cb.getMetrics().getSlowCallRate());
        health.put("total_calls", cb.getMetrics().getNumberOfBufferedCalls());
        health.put("failed_calls", cb.getMetrics().getNumberOfFailedCalls());
        health.put("successful_calls", cb.getMetrics().getNumberOfSuccessfulCalls());

        return ResponseEntity.ok(health);
    }

    @GetMapping("/all-circuits")
    public ResponseEntity<Map<String, String>> allCircuits() {
        Map<String, String> circuits = new HashMap<>();

        circuitBreakerRegistry.getAllCircuitBreakers()
                .forEach(cb -> circuits.put(cb.getName(), cb.getState().toString()));

        return ResponseEntity.ok(circuits);
    }

    @GetMapping("/resilience-status")
    public ResponseEntity<Map<String, Object>> resilienceStatus() {
        CircuitBreaker paymentCB = circuitBreakerRegistry.find("payment-service")
                .orElse(null);

        Map<String, Object> status = new HashMap<>();
        status.put("payment_service_state", paymentCB != null ? paymentCB.getState().toString() : "UNKNOWN");
        status.put("circuit_breaker_enabled", true);
        status.put("retry_enabled", true);
        status.put("fallback_enabled", true);

        return ResponseEntity.ok(status);
    }
}
