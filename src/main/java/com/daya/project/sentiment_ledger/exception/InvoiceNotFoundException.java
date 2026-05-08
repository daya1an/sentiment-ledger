package com.daya.project.sentiment_ledger.exception;

class InvoiceNotFoundException extends BaseException {
    public InvoiceNotFoundException(String invoiceId) {
        super("INVOICE_NOT_FOUND",
                "Invoice not found: " + invoiceId,
                404);
    }
}
