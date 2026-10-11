package com.parth.orderflow.order;

public class InvalidIdempotencyKeyException extends RuntimeException {

    public InvalidIdempotencyKeyException() {
        super("An Idempotency-Key header between 1 and 255 characters is required.");
    }
}
