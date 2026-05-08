package com.daya.project.sentiment_ledger.exception;

/**
 * Thrown when AI service fails
 */
class AIServiceException extends BaseException {
    public AIServiceException(String message) {
        super("AI_SERVICE_ERROR",
                "AI service error: " + message,
                503);
    }
}
