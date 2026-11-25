package com.gkcontas.shipping.catalog;

/**
 * Shipping only cares about weight. Name, price and stock can change shape freely
 * without this service noticing, and its contract says exactly that.
 */
public record ProductWeight(String sku, int weightGrams) {
}
