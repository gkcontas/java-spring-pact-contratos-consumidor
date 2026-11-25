package com.gkcontas.catalog.contract;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Provider states live in test code, not behind an HTTP endpoint in the application:
 * a "/_pact/state" endpoint that rewrites stock is a production liability the day
 * someone forgets to disable the profile that exposes it.
 */
public final class CatalogProviderStates {

    private CatalogProviderStates() {
    }

    public static void productExists(JdbcTemplate jdbc, Map<String, Object> params) {
        String sku = (String) params.get("sku");
        int stock = ((Number) params.getOrDefault("stock", 10)).intValue();
        jdbc.update("DELETE FROM stock_reservations WHERE sku = ?", sku);
        jdbc.update("DELETE FROM products WHERE sku = ?", sku);
        jdbc.update("""
                INSERT INTO products (sku, name, description, price, weight_grams, stock)
                VALUES (?, ?, ?, ?, ?, ?)
                """, sku, "Product " + sku, "State fixture for " + sku, new BigDecimal("349.90"), 950, stock);
    }

    public static void productDoesNotExist(JdbcTemplate jdbc, Map<String, Object> params) {
        String sku = (String) params.get("sku");
        jdbc.update("DELETE FROM stock_reservations WHERE sku = ?", sku);
        jdbc.update("DELETE FROM products WHERE sku = ?", sku);
    }
}
