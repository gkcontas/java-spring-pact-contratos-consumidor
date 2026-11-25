package com.gkcontas.catalog.web;

import com.gkcontas.catalog.product.Product;
import java.math.BigDecimal;

public record ProductResponse(
        String sku,
        String name,
        String description,
        BigDecimal price,
        int weightGrams,
        int stock) {

    static ProductResponse of(Product product) {
        return new ProductResponse(product.getSku(), product.getName(), product.getDescription(),
                product.getPrice(), product.getWeightGrams(), product.getStock());
    }
}
