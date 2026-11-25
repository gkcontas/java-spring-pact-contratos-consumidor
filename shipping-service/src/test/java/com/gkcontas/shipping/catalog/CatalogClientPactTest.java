package com.gkcontas.shipping.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

/**
 * Same endpoint as order-service, a different contract: shipping reads the weight and
 * nothing about price or stock. The provider has to satisfy both at once.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "catalog-service")
class CatalogClientPactTest {

    private static final String CONSUMER = "shipping-service";

    @Autowired
    RestClient.Builder restClientBuilder;

    private CatalogClient client(MockServer mockServer) {
        return new CatalogClient(restClientBuilder.clone(), mockServer.getUrl());
    }

    @Pact(consumer = CONSUMER)
    V4Pact existingProduct(PactDslWithProvider builder) {
        return builder
                .given("a product exists", Map.of("sku", "MS-002", "stock", 10))
                .uponReceiving("a request for the weight of an existing product")
                    .path("/api/products/MS-002")
                    .method("GET")
                    .headers("Accept", "application/json")
                .willRespondWith()
                    .status(200)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .stringType("sku", "MS-002")
                            .integerType("weightGrams", 110))
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "existingProduct")
    void readsTheWeight(MockServer mockServer) {
        assertThat(client(mockServer).findProduct("MS-002")).contains(new ProductWeight("MS-002", 110));
    }

    @Pact(consumer = CONSUMER)
    V4Pact missingProduct(PactDslWithProvider builder) {
        return builder
                .given("a product does not exist", Map.of("sku", "XX-404"))
                .uponReceiving("a request for the weight of a product that does not exist")
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
}
