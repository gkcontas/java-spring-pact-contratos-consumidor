package com.gkcontas.catalog;

import com.gkcontas.catalog.contract.ResponseMutationFilter;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Singleton container, started once per JVM in a static initializer. Never
 * {@code @Testcontainers}: cached Spring contexts would outlive a per-class container.
 *
 * <p>{@link ResponseMutationFilter} is part of every test context (one cached context
 * for all classes); it is inert unless the change matrix names a mutation.
 */
@Import(ResponseMutationFilter.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("catalog")
                    .withUsername("catalog")
                    .withPassword("catalog");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
