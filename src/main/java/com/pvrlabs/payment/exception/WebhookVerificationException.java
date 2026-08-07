package com.pvrlabs.payment.exception;

import org.springframework.http.HttpStatus;

public class WebhookVerificationException extends PaymentException {

    public WebhookVerificationException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "WEBHOOK_VERIFICATION_FAILED");
    }

    public WebhookVerificationException(String message, Throwable cause) {
        super(message, HttpStatus.UNAUTHORIZED, "WEBHOOK_VERIFICATION_FAILED");
        initCause(cause);
    }
}
