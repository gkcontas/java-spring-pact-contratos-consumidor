package com.gkcontas.order.catalog;

import java.math.BigDecimal;

/**
 * Only the fields this service reads. Whatever else the catalog returns is ignored,
 * and is therefore not part of the contract this service publishes.
 */
public record ProductView(String sku, String name, BigDecimal price) {
}
