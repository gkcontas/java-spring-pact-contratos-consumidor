package com.gkcontas.order.catalog;

import lombok.Getter;

@Getter
public class OutOfStockException extends RuntimeException {

    private final int available;

    public OutOfStockException(String sku, int available) {
        super("Product " + sku + " has only " + available + " units available");
        this.available = available;
    }
}
