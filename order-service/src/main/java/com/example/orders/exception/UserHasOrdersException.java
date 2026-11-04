package com.example.orders.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.CONFLICT)
public class UserHasOrdersException extends RuntimeException {
    public UserHasOrdersException(UUID id, int orderCount) {
        super("User " + id + " has " + orderCount + " order(s) and cannot be deleted");
    }
}
