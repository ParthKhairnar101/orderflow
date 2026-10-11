package com.parth.orderflow.order;

import java.util.UUID;

public class DuplicateOrderProductException extends RuntimeException {

    public DuplicateOrderProductException(UUID productId) {
        super("Product " + productId + " appears more than once in the order.");
    }
}
