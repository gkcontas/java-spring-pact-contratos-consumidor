package com.gkcontas.shipping.catalog;

import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

public class CatalogClient {

    private final RestClient http;

    public CatalogClient(RestClient.Builder builder, String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    public Optional<ProductWeight> findProduct(String sku) {
        try {
            return Optional.ofNullable(http.get()
                    .uri("/api/products/{sku}", sku)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ProductWeight.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
