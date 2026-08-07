package com.pvrlabs.payment.exception;

import org.springframework.http.HttpStatus;

public class PaymentException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public PaymentException(String message) {
        this(message, HttpStatus.BAD_GATEWAY, "PAYMENT_ERROR");
    }

    public PaymentException(String message, HttpStatus status) {
        this(message, status, "PAYMENT_ERROR");
    }

    public PaymentException(String message, HttpStatus status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public PaymentException(String message, Throwable cause) {
        super(message, cause);
        this.status = HttpStatus.BAD_GATEWAY;
        this.code = "PAYMENT_ERROR";
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
