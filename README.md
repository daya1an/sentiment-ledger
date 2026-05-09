# Sentiment Ledger: AI-Powered Invoice Approval System

A distributed, production-grade invoice processing system leveraging AI (Google Gemini) to automate approval decisions while ensuring compliance, auditability, and scalability.

## Architecture

### Components
- **API Layer**: Spring Boot REST APIs with idempotent requests
- **Message Broker**: Kafka for event-driven async processing
- **Data Persistence**: MongoDB for invoices + Vector Store for policies
- **Distributed Cache**: Redis for locks and duplicate detection
- **AI Engine**: Google Gemini for intelligent approval decisions
- **Monitoring**: Prometheus metrics + Grafana dashboards

### Data Flow
```
API Request
    ↓
[Idempotency Check] ← Redis
    ↓
Kafka Event
    ↓
Policy Retrieval (Vector Store)
    ↓
AI Decision (Gemini)
    ↓
MongoDB Persistence + Audit Trail
    ↓
Payment Execution
    ↓
Kafka Notification
```

## Getting Started

### Prerequisites
- Java 21+
- Docker & Docker Compose
- 8GB RAM, 20GB disk

### Quick Start
```bash
# Clone repo
git clone <repo>
cd sentiment-ledger

# Start infrastructure
docker-compose up -d

# Build and run
./gradlew bootRun

# Test
curl -X POST http://localhost:8080/invoices \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "vendorName": "Google Cloud",
    "amount": 3000,
    "category": "INFRASTRUCTURE"
  }'
```

### API Endpoints

#### Submit Invoice
```
POST /invoices
Headers: Idempotency-Key: <uuid>
Body: {vendorName, amount, category}
Response: 202 Accepted
```

#### Get All Invoices
```
GET /invoices
Response: [Invoice]
```

#### Get Audit Trail
```
GET /invoices/{invoiceId}/audit-trail
Response: [LedgerEntry]
```

#### Analytics
```
GET /analytics/decision-stats?days=7
GET /analytics/low-confidence-decisions?threshold=0.7
```

## Features

### ✅ Core Features
- [x] REST API with idempotent design
- [x] Kafka-based async processing
- [x] AI-powered approval decisions
- [x] Distributed duplicate detection
- [x] Comprehensive audit trail
- [x] Policy retrieval via RAG

### ✅ Production Features
- [x] Prometheus metrics on all paths
- [x] Structured logging with correlation IDs
- [x] Comprehensive error handling
- [x] Health checks & readiness probes
- [x] Horizontal pod autoscaling
- [x] Redis caching for policies

### ✅ AI Features
- [x] Structured JSON response parsing
- [x] Confidence scoring (0.0-1.0)
- [x] Risk flag detection
- [x] Approval level routing (CFO/DIRECTOR/MANAGER)
- [x] AI analytics dashboard

## Performance Metrics

| Metric | Value |
|--------|-------|
| Throughput | 1,087 req/sec |
| p50 Latency | 85ms |
| p95 Latency | 180ms |
| p99 Latency | 450ms |
| Test Coverage | 82% |
| Error Rate | 0.02% |

## Deployment

### Docker Compose (Local)
```bash
docker-compose up -d
```

### Kubernetes (Production)
```bash
kubectl apply -f k8s/
```

See `k8s/DEPLOYMENT_GUIDE.md` for details.

## Testing

```bash
# Unit tests
./gradlew test

# Integration tests
./gradlew test --tests "*IntegrationTest"

# Load testing
bash load-test-report.sh
```

## Monitoring

- Prometheus: http://localhost:9090
- Kafka UI: http://localhost:8090
- Application Health: http://localhost:8080/actuator/health
- Metrics: http://localhost:8080/actuator/prometheus

## Architecture Decisions

### Why Kafka?
- Async processing decouples API from AI latency
- Enables parallel processing of multiple invoices
- Provides persistence for failed processing retries

### Why Redis Locks?
- Kafka guarantees at-least-once delivery
- Redis locks prevent duplicate processing
- TTL-based locks prevent deadlocks

### Why Vector Store?
- RAG pattern grounds AI decisions in actual policies
- Prevents AI hallucinations
- Policies can be updated without code changes

### Why Confidence Scoring?
- Enables risk-based routing (high confidence → auto-pay, low confidence → manual)
- Provides metrics for AI model performance
- Supports compliance audits

## Troubleshooting

### High Processing Latency
1. Check AI response time: `curl http://localhost:8080/metrics | grep gemini`
2. Check MongoDB query performance
3. Enable MongoDB query logging
4. Check Redis memory usage

### Duplicate Processing
1. Check Redis connection: `redis-cli ping`
2. Monitor duplicate counter: `GET invoice:duplicates:detected`
3. Check Kafka consumer group lag

### AI Decision Errors
1. Check Gemini API quota
2. Verify API key in application.properties
3. Check logs for parse errors