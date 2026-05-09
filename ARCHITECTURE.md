# Detailed Architecture Guide

## System Design

### Idempotency Pattern
Every API request is idempotent via client-provided idempotency keys:
- Client sends `Idempotency-Key` header (UUID)
- Server stores request + response in MongoDB
- Retry with same key returns cached response
- Prevents duplicate payments on network failures

### Distributed Duplicate Detection
Kafka guarantees at-least-once delivery (not exactly-once):
- Redis SETNX (SET if Not eXists) acts as distributed lock
- Invoice consumer tries to acquire lock before processing
- Lock TTL = 24 hours (prevents zombie locks)
- Duplicate messages are dropped with warning

### Event Sourcing for Audit Trail
Every action captured in immutable ledger:
- Invoice created → CREATED event
- AI decision made → DECISION event (with confidence, reasoning)
- Payment executed → PAID event
- On failure → FAILED event with error details
- Enables forensic analysis of any approval

### RAG Pattern for Policy Enforcement
Policy context injected into AI prompts:
1. Policy ingestion: financial-policies.txt chunked and embedded
2. At decision time: vector similarity search retrieves relevant policies
3. Policies injected into AI prompt
4. AI grounds decision in actual policies (not hallucinations)

## Operational Patterns

### Circuit Breaker (Future Enhancement)
```
Open → Payment service down? Stop retrying
Half-Open → Try single request
Closed → Normal operation
```

### Bulkhead Isolation (Future)
- Separate thread pools for AI vs. payment
- One slow service doesn't block others

### Saga Pattern (Future)
- For multi-step approval chains
- Automatic compensation on failure

## Scaling Strategy

### Horizontal Scaling
- 3 Kafka partitions → 3 concurrent invoice processors
- More partitions = more parallel processing
- MongoDB sharding on invoiceId for large datasets

### Vertical Scaling
- Increase container memory for higher cache hit rates
- Increase CPU for faster AI processing

### Caching Strategy
- Policy context cached 1 hour (invalidate on update)
- Hit rate target: >85%
- Monitor: `GET /metrics | grep cache`

## Data Consistency

### MongoDB Write Concerns
- journalWrites=true (mandatory before response)
- replication factor = 3 (production)

### Kafka Consumer Offsets
- Auto-commit disabled (prevent data loss on crash)
- Manual commit after successful MongoDB write

### Exactly-Once Semantics
- Redis lock + Idempotency key = exactly-once processing
- Even if Kafka retries, response is idempotent