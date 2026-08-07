package com.pvrlabs.payment.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends PaymentException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }
}
