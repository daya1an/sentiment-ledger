package com.daya.project.sentiment_ledger.exception;

class InvalidInvoiceException extends BaseException {
    public InvalidInvoiceException(String field, String reason) {
        super("INVALID_INVOICE",
                "Invalid invoice field '" + field + "': " + reason,
                400);
    }
}
