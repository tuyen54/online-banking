package com.onlinebanking.exceptions;

/**
 * Thrown when an Idempotency-Key is reused with a different request payload.
 * Maps to HTTP 409 Conflict.
 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}

