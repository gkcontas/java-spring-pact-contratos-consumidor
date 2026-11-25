package com.gkcontas.order.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

/**
 * Each interaction lists only what {@link CatalogClient} reads. The body uses type
 * matchers: the example values are what the mock server answers with, not what the
 * provider has to return. The one literal is the error {@code code}, because the client
 * branches on its exact value.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "catalog-service")
class CatalogClientPactTest {

    private static final String CONSUMER = "order-service";

    @Autowired
    RestClient.Builder restClientBuilder;

    private CatalogClient client(MockServer mockServer) {
        return new CatalogClient(restClientBuilder.clone(), mockServer.getUrl());
    }

    @Pact(consumer = CONSUMER)
    V4Pact existingProduct(PactDslWithProvider builder) {
        return builder
                .given("a product exists", Map.of("sku", "KB-001", "stock", 10))
                .uponReceiving("a request for an existing product")
                    .path("/api/products/KB-001")
                    .method("GET")
                    .headers("Accept", "application/json")
                .willRespondWith()
                    .status(200)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .stringType("sku", "KB-001")
                            .stringType("name", "Mechanical keyboard")
                            .decimalType("price", new BigDecimal("349.90")))
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "existingProduct")
    void readsAnExistingProduct(MockServer mockServer) {
        assertThat(client(mockServer).findProduct("KB-001"))
                .contains(new ProductView("KB-001", "Mechanical keyboard", new BigDecimal("349.90")));
    }

    @Pact(consumer = CONSUMER)
    V4Pact missingProduct(PactDslWithProvider builder) {
        return builder
                .given("a product does not exist", Map.of("sku", "XX-404"))
                .uponReceiving("a request for a product that does not exist")
                    .path("/api/products/XX-404")
                    .method("GET")
                    .headers("Accept", "application/json")
                .willRespondWith()
                    .status(404)
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "missingProduct")
    void treatsNotFoundAsAbsent(MockServer mockServer) {
        assertThat(client(mockServer).findProduct("XX-404")).isEmpty();
    }

    @Pact(consumer = CONSUMER)
    V4Pact successfulReservation(PactDslWithProvider builder) {
        return builder
                .given("a product exists", Map.of("sku", "KB-001", "stock", 10))
                .uponReceiving("a reservation within the available stock")
                    .path("/api/stock-reservations")
                    .method("POST")
                    .headers("Content-Type", "application/json")
                    .body(new PactDslJsonBody()
                            .stringValue("sku", "KB-001")
                            .integerType("quantity", 2)
                            .stringMatcher("orderRef", "ORD-[0-9a-f-]{36}", "ORD-6f1c2a1e-0a3b-4f8e-9a51-1d2c3b4a5e6f"))
                .willRespondWith()
                    .status(201)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .uuid("reservationId", "3f0d7b1e-8c2a-4f61-9b0e-5a7c9d1e2f30"))
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "successfulReservation")
    void reservesStock(MockServer mockServer) {
        ReservationView reservation = client(mockServer)
                .reserve("KB-001", 2, "ORD-6f1c2a1e-0a3b-4f8e-9a51-1d2c3b4a5e6f");

        assertThat(reservation.reservationId()).hasToString("3f0d7b1e-8c2a-4f61-9b0e-5a7c9d1e2f30");
    }

    @Pact(consumer = CONSUMER)
    V4Pact insufficientStock(PactDslWithProvider builder) {
        return builder
                .given("a product exists", Map.of("sku", "MN-003", "stock", 1))
                .uponReceiving("a reservation above the available stock")
                    .path("/api/stock-reservations")
                    .method("POST")
                    .headers("Content-Type", "application/json")
                    .body(new PactDslJsonBody()
                            .stringValue("sku", "MN-003")
                            .integerType("quantity", 5)
                            .stringMatcher("orderRef", "ORD-[0-9a-f-]{36}", "ORD-0c9d8e7f-6a5b-4c3d-8e2f-1a0b9c8d7e6f"))
                .willRespondWith()
                    .status(409)
                    .headers(Map.of("Content-Type", "application/problem+json"))
                    .body(new PactDslJsonBody()
                            .stringValue("code", "INSUFFICIENT_STOCK")
                            .integerType("available", 1))
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "insufficientStock")
    void reportsInsufficientStock(MockServer mockServer) {
        assertThatThrownBy(() -> client(mockServer)
                .reserve("MN-003", 5, "ORD-0c9d8e7f-6a5b-4c3d-8e2f-1a0b9c8d7e6f"))
                .isInstanceOfSatisfying(OutOfStockException.class,
                        e -> assertThat(e.getAvailable()).isEqualTo(1));
    }
}
