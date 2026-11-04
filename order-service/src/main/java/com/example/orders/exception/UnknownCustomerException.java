package com.example.orders.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class UnknownCustomerException extends RuntimeException {
    public UnknownCustomerException(String customerId) {
        super("Unknown customer " + customerId);
    }
}
