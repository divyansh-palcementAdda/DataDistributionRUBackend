package com.app.datadistribution.integration.meta.exception;

import lombok.Getter;

@Getter
public class MetaGraphApiException extends RuntimeException {

    private final int httpStatus;
    private final Integer errorCode;
    private final Integer errorSubcode;
    private final String errorType;
    private final String metaMessage;
    private final boolean retryable;

    public MetaGraphApiException(int httpStatus, Integer errorCode, Integer errorSubcode,
                                String errorType, String metaMessage, boolean retryable, Throwable cause) {
        super(formatMessage(httpStatus, errorCode, errorSubcode, errorType, metaMessage, retryable), cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.errorSubcode = errorSubcode;
        this.errorType = errorType;
        this.metaMessage = metaMessage;
        this.retryable = retryable;
    }

    public MetaGraphApiException(int httpStatus, String metaMessage, boolean retryable, Throwable cause) {
        this(httpStatus, null, null, null, metaMessage, retryable, cause);
    }

    private static String formatMessage(int httpStatus, Integer errorCode, Integer errorSubcode,
                                       String errorType, String metaMessage, boolean retryable) {
        return String.format("Meta Graph API error [HTTP %d | Code %s | Subcode %s | Type %s | Retryable: %s]: %s",
                httpStatus,
                errorCode != null ? errorCode : "N/A",
                errorSubcode != null ? errorSubcode : "N/A",
                errorType != null ? errorType : "N/A",
                retryable,
                metaMessage != null ? metaMessage : "Unknown");
    }
}
