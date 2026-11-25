package com.gkcontas.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.gkcontas.catalog.IntegrationTestBase;
import com.gkcontas.catalog.contract.CatalogProviderStates;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class CatalogApiIT extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void returnsTheProduct() {
        CatalogProviderStates.productExists(jdbc, Map.of("sku", "T-100", "stock", 4));

        ResponseEntity<JsonNode> response = rest.getForEntity("/api/products/T-100", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("price").decimalValue()).isEqualByComparingTo("349.90");
        assertThat(response.getBody().get("stock").asInt()).isEqualTo(4);
        assertThat(response.getBody().get("weightGrams").asInt()).isEqualTo(950);
    }

    @Test
    void unknownProductIsAProblemWithACode() {
        ResponseEntity<JsonNode> response = rest.getForEntity("/api/products/NOPE", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code").asText()).isEqualTo("PRODUCT_NOT_FOUND");
    }

    @Test
    void reservationDecrementsStock() {
        CatalogProviderStates.productExists(jdbc, Map.of("sku", "T-200", "stock", 5));

        ResponseEntity<JsonNode> response = reserve("T-200", 3);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("remainingStock").asInt()).isEqualTo(2);
        assertThat(stockOf("T-200")).isEqualTo(2);
    }

    @Test
    void reservationAboveStockIsAConflictAndChangesNothing() {
        CatalogProviderStates.productExists(jdbc, Map.of("sku", "T-300", "stock", 1));

        ResponseEntity<JsonNode> response = reserve("T-300", 2);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asText()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(response.getBody().get("available").asInt()).isEqualTo(1);
        assertThat(stockOf("T-300")).isEqualTo(1);
    }

    @Test
    void reservationForUnknownProductIsNotFound() {
        assertThat(reserve("NOPE", 1).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void invalidReservationNamesEveryFieldInEnglish() {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/stock-reservations",
                Map.of("sku", "", "quantity", 0), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail").asText()).isEqualTo(
                "orderRef: orderRef is required; quantity: quantity must be positive; sku: sku is required");
    }

    @Test
    void concurrentReservationsNeverOversell() throws Exception {
        CatalogProviderStates.productExists(jdbc, Map.of("sku", "T-400", "stock", 10));
        List<Callable<HttpStatus>> calls = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            calls.add(() -> HttpStatus.valueOf(reserve("T-400", 1).getStatusCode().value()));
        }

        List<HttpStatus> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(15)) {
            for (Future<HttpStatus> future : pool.invokeAll(calls)) {
                statuses.add(future.get());
            }
        }

        assertThat(statuses).filteredOn(HttpStatus.CREATED::equals).hasSize(10);
        assertThat(statuses).filteredOn(HttpStatus.CONFLICT::equals).hasSize(20);
        assertThat(stockOf("T-400")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_reservations WHERE sku = 'T-400'", Integer.class))
                .isEqualTo(10);
    }

    private ResponseEntity<JsonNode> reserve(String sku, int quantity) {
        return rest.postForEntity("/api/stock-reservations",
                Map.of("sku", sku, "quantity", quantity, "orderRef", "ORD-test"), JsonNode.class);
    }

    private int stockOf(String sku) {
        return jdbc.queryForObject("SELECT stock FROM products WHERE sku = ?", Integer.class, sku);
    }
}
