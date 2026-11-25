package com.gkcontas.catalog.product;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String sku) {
        super("Product " + sku + " not found");
    }
}
