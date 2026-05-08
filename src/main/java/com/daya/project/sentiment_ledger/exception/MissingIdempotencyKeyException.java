package com.daya.project.sentiment_ledger.exception;

class MissingIdempotencyKeyException extends BaseException {
    public MissingIdempotencyKeyException() {
        super("MISSING_IDEMPOTENCY_KEY",
                "Idempotency-Key header is required. Please provide a unique UUID in Idempotency-Key header.",
                400);
    }
}
