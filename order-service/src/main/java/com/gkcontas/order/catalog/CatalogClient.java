package com.gkcontas.order.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * The production HTTP client for catalog-service. The Pact consumer test drives this
 * very class against the mock server: a test that built its own requests would publish
 * a contract for code that never runs in production.
 */
public class CatalogClient {

    private final RestClient http;

    public CatalogClient(RestClient.Builder builder, String baseUrl) {
        // The builder comes from Spring Boot, so it carries Boot's ObjectMapper, which
        // ignores unknown properties. A bare ObjectMapper would fail on the first field
        // the provider adds, and the contract would no longer tell the truth.
        this.http = builder.baseUrl(baseUrl).build();
    }

    public Optional<ProductView> findProduct(String sku) {
        try {
            return Optional.ofNullable(http.get()
                    .uri("/api/products/{sku}", sku)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ProductView.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public ReservationView reserve(String sku, int quantity, String orderRef) {
        try {
            return http.post()
                    .uri("/api/stock-reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of("sku", sku, "quantity", quantity, "orderRef", orderRef))
                    .retrieve()
                    .body(ReservationView.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
                JsonNode problem = e.getResponseBodyAs(JsonNode.class);
                if (problem != null && "INSUFFICIENT_STOCK".equals(problem.path("code").asText())) {
                    throw new OutOfStockException(sku, problem.path("available").asInt());
                }
            }
            throw e;
        }
    }
}
