package com.daya.project.sentiment_ledger.exception;

public class PaymentServiceException extends BaseException {
    public PaymentServiceException(String invoiceId, String message) {
        super("PAYMENT_ERROR",
                "Payment failed for invoice " + invoiceId + ": " + message,
                502);
    }
}
