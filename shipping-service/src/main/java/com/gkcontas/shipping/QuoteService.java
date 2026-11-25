package com.gkcontas.shipping;

import com.gkcontas.shipping.catalog.CatalogClient;
import com.gkcontas.shipping.catalog.ProductWeight;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuoteService {

    static final BigDecimal BASE_FEE = new BigDecimal("12.00");
    static final BigDecimal PER_STARTED_KG = new BigDecimal("4.50");

    private final CatalogClient catalog;

    public record Quote(String sku, int quantity, int totalWeightGrams, BigDecimal price) {
    }

    public static class UnknownProductException extends RuntimeException {
        public UnknownProductException(String sku) {
            super("Product " + sku + " does not exist");
        }
    }

    public Quote quote(String sku, int quantity) {
        ProductWeight product = catalog.findProduct(sku).orElseThrow(() -> new UnknownProductException(sku));
        int totalGrams = product.weightGrams() * quantity;
        int startedKg = (totalGrams + 999) / 1000;
        BigDecimal price = BASE_FEE.add(PER_STARTED_KG.multiply(BigDecimal.valueOf(startedKg)));
        return new Quote(product.sku(), quantity, totalGrams, price);
    }
}
