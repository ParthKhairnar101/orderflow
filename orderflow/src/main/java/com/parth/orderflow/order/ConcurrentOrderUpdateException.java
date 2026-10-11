package com.parth.orderflow.order;

public class ConcurrentOrderUpdateException extends RuntimeException {

    public ConcurrentOrderUpdateException() {
        super("Inventory changed while processing the order. Reload inventory and retry.");
    }
}
