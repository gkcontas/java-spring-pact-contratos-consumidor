package com.gkcontas.catalog.reservation;

import lombok.Getter;

@Getter
public class InsufficientStockException extends RuntimeException {

    private final int available;

    public InsufficientStockException(String sku, int requested, int available) {
        super("Requested " + requested + " units of " + sku + " but only " + available + " are available");
        this.available = available;
    }
}
