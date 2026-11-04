package com.example.orders.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidIdempotencyKeyException extends RuntimeException {
    public InvalidIdempotencyKeyException(int maxLength) {
        super("Idempotency-Key must be 1-" + maxLength + " characters");
    }
}
