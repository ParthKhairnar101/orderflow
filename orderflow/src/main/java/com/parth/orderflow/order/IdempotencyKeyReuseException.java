package com.parth.orderflow.order;

public class IdempotencyKeyReuseException extends RuntimeException {

    public IdempotencyKeyReuseException() {
        super("The Idempotency-Key was already used with a different order request.");
    }
}
