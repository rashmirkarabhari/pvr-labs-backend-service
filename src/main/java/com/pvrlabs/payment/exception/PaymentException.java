package com.pvrlabs.payment.exception;

import org.springframework.http.HttpStatus;

public class PaymentException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String cashfreeType;
    private final String cashfreeRawBody;

    public PaymentException(String message) {
        this(message, HttpStatus.BAD_GATEWAY, "PAYMENT_ERROR", null, null);
    }

    public PaymentException(String message, HttpStatus status) {
        this(message, status, "PAYMENT_ERROR", null, null);
    }

    public PaymentException(String message, HttpStatus status, String code) {
        this(message, status, code, null, null);
    }

    public PaymentException(String message, HttpStatus status, String code,
                            String cashfreeType, String cashfreeRawBody) {
        super(message);
        this.status = status;
        this.code = code;
        this.cashfreeType = cashfreeType;
        this.cashfreeRawBody = cashfreeRawBody;
    }

    public PaymentException(String message, Throwable cause) {
        super(message, cause);
        this.status = HttpStatus.BAD_GATEWAY;
        this.code = "PAYMENT_ERROR";
        this.cashfreeType = null;
        this.cashfreeRawBody = null;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getCashfreeType() {
        return cashfreeType;
    }

    public String getCashfreeRawBody() {
        return cashfreeRawBody;
    }
}
