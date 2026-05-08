package com.daya.project.sentiment_ledger.exception;

class DuplicateInvoiceException extends BaseException {
    public DuplicateInvoiceException(String invoiceId) {
        super("DUPLICATE_INVOICE",
                "Invoice already processed: " + invoiceId,
                409);
    }
}
