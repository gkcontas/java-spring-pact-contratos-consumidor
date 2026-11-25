package com.gkcontas.catalog.product;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository products;

    @Transactional(readOnly = true)
    public Product find(String sku) {
        return products.findById(sku).orElseThrow(() -> new ProductNotFoundException(sku));
    }
}
