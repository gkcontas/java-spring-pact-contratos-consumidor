package com.gkcontas.order.catalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
class CatalogClientConfig {

    @Bean
    CatalogClient catalogClient(RestClient.Builder builder, @Value("${catalog.base-url}") String baseUrl) {
        return new CatalogClient(builder, baseUrl);
    }
}
