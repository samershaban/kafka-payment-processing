package com.example.orders.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class IdempotencyKeyReusedException extends RuntimeException {
    public IdempotencyKeyReusedException() {
        super("Idempotency-Key was already used for a different order");
    }
}
