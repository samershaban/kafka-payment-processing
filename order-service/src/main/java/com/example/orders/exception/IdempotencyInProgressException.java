package com.example.orders.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The key was claimed by another request whose order isn't saved yet. The client should retry shortly. */
@ResponseStatus(HttpStatus.CONFLICT)
public class IdempotencyInProgressException extends RuntimeException {
    public IdempotencyInProgressException() {
        super("An order with this Idempotency-Key is still being created; retry shortly");
    }
}
