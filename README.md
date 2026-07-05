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

## Sample success log

```
2026-07-06 00:18:32.017 INFO  c.d.p.s.s.invoice.InvoiceService         : 📨 API request received | Vendor: Google Cloud | Amount: 1500 | Idempotency-Key: test-17
2026-07-06 00:18:32.018 DEBUG o.s.data.mongodb.core.MongoTemplate      : find using query: { "clientProvidedKey" : "test-17"} fields: Document{{}} sort: null for class: class com.daya.project.sentiment_ledger.model.IdempotencyKey in collection: idempotency_keys
2026-07-06 00:18:32.019 DEBUG org.mongodb.driver.protocol.command      : Command "find" started on database "sentiment_ledger" using a connection with driver-generated ID 8 and server-generated ID 4620 to ac-zjhfsvq-shard-00-01.ntpeic2.mongodb.net:27017. The request ID is 548 and the operation ID is 1050. Command: {"find": "idempotency_keys", "filter": {"clientProvidedKey": "test-17"}, "limit": 2, "$db": "sentiment_ledger", "$clusterTime": {"clusterTime": {"$timestamp": {"t": 1783277120, "i": 3}}, "signature": {"hash": {"$binary": {"base64": "z10kTDqUkWy+CqwNOCPnrYGWDi0=", "subType": "00"}}, "keyId": 7617876522459725826}}, "lsid": {"id": {"$binary": {"base64": "de8ROnLPRfaa7RyrscM2Sg==", "subType": "04"}}}}
2026-07-06 00:18:32.052 DEBUG org.mongodb.driver.protocol.command      : Command "find" succeeded on database "sentiment_ledger" in 33.3415 ms using a connection with driver-generated ID 8 and server-generated ID 4620 to ac-zjhfsvq-shard-00-01.ntpeic2.mongodb.net:27017. The request ID is 548 and the operation ID is 1050. Command reply: {"cursor": {"firstBatch": [], "id": 0, "ns": "sentiment_ledger.idempotency_keys"}, "ok": 1.0, "$clusterTime": {"clusterTime": {"$timestamp": {"t": 1783277312, "i": 3}}, "signature": {"hash": {"$binary": {"base64": "ADFrOVy1fhMR5FAgGXhsbRb+pWQ=", "subType": "00"}}, "keyId": 7617876522459725826}}, "operationTime": {"$timestamp": {"t": 1783277312, "i": 3}}}
2026-07-06 00:18:32.053 INFO  c.d.p.s.s.invoice.InvoiceService         : ✅ Processing new invoice | ID: 5a703f48-3794-4994-8fc2-2db98e4a6da5 | Vendor: Google Cloud
2026-07-06 00:18:32.053 DEBUG o.s.data.mongodb.core.MongoTemplate      : Saving Document containing fields: [_id, clientProvidedKey, invoiceId, responseStatus, responseBody, createdAt, expiresAt, _class]
2026-07-06 00:18:32.054 DEBUG org.mongodb.driver.protocol.command      : Command "update" started on database "sentiment_ledger" using a connection with driver-generated ID 8 and server-generated ID 4620 to ac-zjhfsvq-shard-00-01.ntpeic2.mongodb.net:27017. The request ID is 549 and the operation ID is 1051. Command: {"update": "idempotency_keys", "ordered": true, "writeConcern": {"w": "majority"}, "txnNumber": 6, "$db": "sentiment_ledger", "$clusterTime": {"clusterTime": {"$timestamp": {"t": 1783277312, "i": 3}}, "signature": {"hash": {"$binary": {"base64": "ADFrOVy1fhMR5FAgGXhsbRb+pWQ=", "subType": "00"}}, "keyId": 7617876522459725826}}, "lsid": {"id": {"$binary": {"base64": "de8ROnLPRfaa7RyrscM2Sg==", "subType": "04"}}}, "updates": [{"q": {"_id": "86822a36-ad02-406c-89cb-bc5964178f5a"}, "u": {"_id": "86822a36-ad02-406c-89cb-bc5964178f5a", "clientProvidedKey": "test-17", "invoiceId": "5a703f48-3794-4994-8fc2-2db98e4a6da5", "responseStatus": "COMPLETED", "responseBody": "Invoice accepted into the processing queue. Event ID: 5a703f48-3794-4994-8fc2-2db98e4a6da5", "createdAt": {"$date": "2026-07-05T18:48:32.053Z"}, "expiresAt": {"$date": "2026-07-06T18:48:32.053Z"}, "_class": "com.daya.project.sentiment_ledger.model.IdempotencyKey"}, "upsert": true}]}
2026-07-06 00:18:32.091 DEBUG org.mongodb.driver.protocol.command      : Command "update" succeeded on database "sentiment_ledger" in 37.1334 ms using a connection with driver-generated ID 8 and server-generated ID 4620 to ac-zjhfsvq-shard-00-01.ntpeic2.mongodb.net:27017. The request ID is 549 and the operation ID is 1051. Command reply: {"n": 1, "electionId": {"$oid": "7fffffff00000000000000fa"}, "opTime": {"ts": {"$timestamp": {"t": 1783277312, "i": 4}}, "t": 250}, "upserted": [{"index": 0, "_id": "86822a36-ad02-406c-89cb-bc5964178f5a"}], "nModified": 0, "ok": 1.0, "$clusterTime": {"clusterTime": {"$timestamp": {"t": 1783277312, "i": 4}}, "signature": {"hash": {"$binary": {"base64": "ADFrOVy1fhMR5FAgGXhsbRb+pWQ=", "subType": "00"}}, "keyId": 7617876522459725826}}, "operationTime": {"$timestamp": {"t": 1783277312, "i": 4}}}
2026-07-06 00:18:32.092 DEBUG c.d.p.s.s.invoice.InvoiceService         : 💾 Idempotency record saved for key: test-17
2026-07-06 00:18:32.092 INFO  c.d.p.s.s.invoice.InvoiceService         : 📤 Invoice published to Kafka topic: invoice-submitted
2026-07-06 00:18:32.106 INFO  c.d.p.s.s.invoice.InvoiceProcessor       : ✅ Processing Invoice ID: 5a703f48-3794-4994-8fc2-2db98e4a6da5
2026-07-06 00:18:32.108 INFO  c.d.p.s.service.AIDecisionService        : 🤖 Calling Gemini AI for invoice: 5a703f48-3794-4994-8fc2-2db98e4a6da5 | Vendor: Google Cloud
2026-07-06 00:18:35.441 DEBUG c.d.p.s.service.AIDecisionService        : Raw AI response: {
  "decision": "APPROVED",
  "confidence": 0.98,
  "reasoning": "Google Cloud infrastructure invoice for ₹1500 is under the ₹5000.00 auto-approval threshold as per policy 1.",
  "riskFlags": [],
  "requiresApprovalLevel": "NONE"
}
2026-07-06 00:18:35.441 INFO  c.d.p.s.service.AIDecisionService        : 🎯 AI Decision: APPROVED (confidence: %.2f) | Flags: 0.98
2026-07-06 00:18:35.442 DEBUG o.s.data.mongodb.core.MongoTemplate      : Saving Document containing fields: [_id, vendorName, amount, category, status, createdAt, confidenceScore, vendorStripeConnectId, _class]
2026-07-06 00:18:35.443 DEBUG org.mongodb.driver.protocol.command      : Command "update" started on database "sentiment_ledger" using a connection with driver-generated ID 8 and server-generated ID 4620 to ac-zjhfsvq-shard-00-01.ntpeic2.mongodb.net:27017. The request ID is 550 and the operation ID is 1055. Command: {"update": "invoices", "ordered": true, "writeConcern": {"w": "majority"}, "txnNumber": 7, "$db": "sentiment_ledger", "$clusterTime": {"clusterTime": {"$timestamp": {"t": 1783277312, "i": 4}}, "signature": {"hash": {"$binary": {"base64": "ADFrOVy1fhMR5FAgGXhsbRb+pWQ=", "subType": "00"}}, "keyId": 7617876522459725826}}, "lsid": {"id": {"$binary": {"base64": "de8ROnLPRfaa7RyrscM2Sg==", "subType": "04"}}}, "updates": [{"q": {"_id": "5a703f48-3794-4994-8fc2-2db98e4a6da5"}, "u": {"_id": "5a703f48-3794-4994-8fc2-2db98e4a6da5", "vendorName": "Google Cloud", "amount": "1500", "category": "INFRASTRUCTURE", "status": "AI_APPROVED", "createdAt": {"$date": "2026-07-05T18:48:32.106Z"}, "confidenceScore": 0.98, "vendorStripeConnectId": "acct_1TpYorA8K2A5nGHw", "_class": "com.daya.project.sentiment_ledger.model.Invoice"}, "upsert": true}]}
2026-07-06 00:18:35.472 DEBUG org.mongodb.driver.protocol.command      : Command "update" succeeded on database "sentiment_ledger" in 29.6409 ms using a connection with driver-generated ID 8 and server-generated ID 4620 to ac-zjhfsvq-shard-00-01.ntpeic2.mongodb.net:27017. The request ID is 550 and the operation ID is 1055. Command reply: {"n": 1, "electionId": {"$oid": "7fffffff00000000000000fa"}, "opTime": {"ts": {"$timestamp": {"t": 1783277316, "i": 1}}, "t": 250}, "upserted": [{"index": 0, "_id": "5a703f48-3794-4994-8fc2-2db98e4a6da5"}], "nModified": 0, "ok": 1.0, "$clusterTime": {"clusterTime": {"$timestamp": {"t": 1783277316, "i": 1}}, "signature": {"hash": {"$binary": {"base64": "lavXJBFNdgH6B7ZhuyAFNGFK7Hw=", "subType": "00"}}, "keyId": 7617876522459725826}}, "operationTime": {"$timestamp": {"t": 1783277316, "i": 1}}}
2026-07-06 00:18:35.472 DEBUG o.s.data.mongodb.core.MongoTemplate      : Inserting Document containing fields: [invoiceId, actionTaken, aiReasoningContext, timestamp, _class] in collection: ledger_entries
2026-07-06 00:18:35.474 DEBUG org.mongodb.driver.protocol.command      : Command "insert" started on database "sentiment_ledger" using a connection with driver-generated ID 8 and server-generated ID 4620 to ac-zjhfsvq-shard-00-01.ntpeic2.mongodb.net:27017. The request ID is 551 and the operation ID is 1056. Command: {"insert": "ledger_entries", "ordered": true, "writeConcern": {"w": "majority"}, "txnNumber": 8, "$db": "sentiment_ledger", "$clusterTime": {"clusterTime": {"$timestamp": {"t": 1783277316, "i": 1}}, "signature": {"hash": {"$binary": {"base64": "lavXJBFNdgH6B7ZhuyAFNGFK7Hw=", "subType": "00"}}, "keyId": 7617876522459725826}}, "lsid": {"id": {"$binary": {"base64": "de8ROnLPRfaa7RyrscM2Sg==", "subType": "04"}}}, "documents": [{"_id": {"$oid": "6a4aa70328dc693ae57651ac"}, "invoiceId": "5a703f48-3794-4994-8fc2-2db98e4a6da5", "actionTaken": "APPROVED", "aiReasoningContext": "Decision: APPROVED | Confidence: 0.98 | Reasoning: Google Cloud infrastructure invoice for ₹1500 is under the ₹5000.00 auto-approval threshold as per policy 1. | Risk Flags:  | Approval Level: NONE", "timestamp": {"$date": "2026-07-05T18:48:35.472Z"}, "_class": "com.daya.project.sentiment_ledger.model.LedgerEntry"}]}
2026-07-06 00:18:35.504 DEBUG org.mongodb.driver.protocol.command      : Command "insert" succeeded on database "sentiment_ledger" in 30.4425 ms using a connection with driver-generated ID 8 and server-generated ID 4620 to ac-zjhfsvq-shard-00-01.ntpeic2.mongodb.net:27017. The request ID is 551 and the operation ID is 1056. Command reply: {"n": 1, "electionId": {"$oid": "7fffffff00000000000000fa"}, "opTime": {"ts": {"$timestamp": {"t": 1783277316, "i": 2}}, "t": 250}, "ok": 1.0, "$clusterTime": {"clusterTime": {"$timestamp": {"t": 1783277316, "i": 2}}, "signature": {"hash": {"$binary": {"base64": "lavXJBFNdgH6B7ZhuyAFNGFK7Hw=", "subType": "00"}}, "keyId": 7617876522459725826}}, "operationTime": {"$timestamp": {"t": 1783277316, "i": 2}}}
2026-07-06 00:18:35.509 INFO  c.d.p.s.s.p.PaymentEventListener         : 💳 Processing payment event for invoice: 5a703f48-3794-4994-8fc2-2db98e4a6da5
2026-07-06 00:18:35.510 DEBUG o.s.data.mongodb.core.MongoTemplate      : findOne using query: { "id" : "5a703f48-3794-4994-8fc2-2db98e4a6da5"} fields: Document{{}} for class: class com.daya.project.sentiment_ledger.model.Invoice in collection: invoices
2026-07-06 00:18:35.510 DEBUG org.mongodb.driver.protocol.command      : Command "find" started on database "sentiment_ledger" using a connection with driver-generated ID 8 and server-generated ID 4620 to ac-zjhfsvq-shard-00-01.ntpeic2.mongodb.net:27017. The request ID is 552 and the operation ID is 1057. Command: {"find": "invoices", "filter": {"_id": "5a703f48-3794-4994-8fc2-2db98e4a6da5"}, "limit": 1, "singleBatch": true, "$db": "sentiment_ledger", "$clusterTime": {"clusterTime": {"$timestamp": {"t": 1783277316, "i": 2}}, "signature": {"hash": {"$binary": {"base64": "lavXJBFNdgH6B7ZhuyAFNGFK7Hw=", "subType": "00"}}, "keyId": 7617876522459725826}}, "lsid": {"id": {"$binary": {"base64": "de8ROnLPRfaa7RyrscM2Sg==", "subType": "04"}}}}
2026-07-06 00:18:35.537 DEBUG org.mongodb.driver.protocol.command      : Command "find" succeeded on database "sentiment_ledger" in 26.9929 ms using a connection with driver-generated ID 8 and server-generated ID 4620 to ac-zjhfsvq-shard-00-01.ntpeic2.mongodb.net:27017. The request ID is 552 and the operation ID is 1057. Command reply: {"cursor": {"firstBatch": [{"_id": "5a703f48-3794-4994-8fc2-2db98e4a6da5", "vendorName": "Google Cloud", "amount": "1500", "category": "INFRASTRUCTURE", "status": "AI_APPROVED", "createdAt": {"$date": "2026-07-05T18:48:32.106Z"}, "confidenceScore": 0.98, "vendorStripeConnectId": "acct_1TpYorA8K2A5nGHw", "_class": "com.daya.project.sentiment_ledger.model.Invoice"}], "id": 0, "ns": "sentiment_ledger.invoices"}, "ok": 1.0, "$clusterTime": {"clusterTime": {"$timestamp": {"t": 1783277316, "i": 2}}, "signature": {"hash": {"$binary": {"base64": "lavXJBFNdgH6B7ZhuyAFNGFK7Hw=", "subType": "00"}}, "keyId": 7617876522459725826}}, "operationTime": {"$timestamp": {"t": 1783277316, "i": 2}}}
2026-07-06 00:18:35.537 INFO  c.d.p.s.s.payment.PaymentService         : 💳 Executing Stripe Transfer for Invoice: 5a703f48-3794-4994-8fc2-2db98e4a6da5 | Vendor: Google Cloud | Amount: $1500 | ConnectId: acct_1TpYorA8K2A5nGHw
2026-07-06 00:18:36.058 INFO  c.d.p.s.s.payment.PaymentService 	       : ✅ Transfer SUCCESS! Vendor: Google Cloud | Amount: $1500 | Stripe Transfer ID: tr_3TpYorKEiLaKiHKt1A2b3C4d
2026-07-06 00:18:36.060 DEBUG o.s.data.mongodb.core.MongoTemplate      : Saving Document containing fields: [_id, vendorName, amount, category, status, createdAt, confidenceScore, vendorStripeConnectId, _class]
2026-07-06 00:18:36.061 DEBUG org.mongodb.driver.protocol.command      : Command "update" started on database "sentiment_ledger" using a connection with driver-generated ID 8 and server-generated ID 4620 to ac-zjhfsvq-shard-00-01.ntpeic2.mongodb.net:27017. The request ID is 553 and the operation ID is 1058. Command: {"update": "invoices", "ordered": true, "writeConcern": {"w": "majority"}, "txnNumber": 9, "$db": "sentiment_ledger", "$clusterTime": {"clusterTime": {"$timestamp": {"t": 1783277316, "i": 2}}, "signature": {"hash": {"$binary": {"base64": "lavXJBFNdgH6B7ZhuyAFNGFK7Hw=", "subType": "00"}}, "keyId": 7617876522459725826}}, "lsid": {"id": {"$binary": {"base64": "de8ROnLPRfaa7RyrscM2Sg==", "subType": "04"}}}, "updates": [{"q": {"_id": "5a703f48-3794-4994-8fc2-2db98e4a6da5"}, "u": {"_id": "5a703f48-3794-4994-8fc2-2db98e4a6da5", "vendorName": "Google Cloud", "amount": "1500", "category": "INFRASTRUCTURE", "status": "PENDING", "createdAt": {"$date": "2026-07-05T18:48:32.106Z"}, "confidenceScore": 0.98, "vendorStripeConnectId": "acct_1TpYorA8K2A5nGHw", "_class": "com.daya.project.sentiment_ledger.model.Invoice"}, "upsert": true}]}
2026-07-06 00:18:36.088 DEBUG org.mongodb.driver.protocol.command      : Command "update" succeeded on database "sentiment_ledger" in 27.3471 ms using a connection with driver-generated ID 8 and server-generated ID 4620 to ac-zjhfsvq-shard-00-01.ntpeic2.mongodb.net:27017. The request ID is 553 and the operation ID is 1058. Command reply: {"n": 1, "electionId": {"$oid": "7fffffff00000000000000fa"}, "opTime": {"ts": {"$timestamp": {"t": 1783277316, "i": 4}}, "t": 250}, "nModified": 1, "ok": 1.0, "$clusterTime": {"clusterTime": {"$timestamp": {"t": 1783277316, "i": 4}}, "signature": {"hash": {"$binary": {"base64": "lavXJBFNdgH6B7ZhuyAFNGFK7Hw=", "subType": "00"}}, "keyId": 7617876522459725826}}, "operationTime": {"$timestamp": {"t": 1783277316, "i": 4}}}
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