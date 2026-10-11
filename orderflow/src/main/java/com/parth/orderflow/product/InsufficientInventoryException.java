package com.parth.orderflow.product;

public class InsufficientInventoryException extends RuntimeException {

    public InsufficientInventoryException(String sku, int available, int requested) {
        super("Insufficient inventory for SKU '" + sku + "': " + available
                + " available, " + requested + " requested.");
    }
}
