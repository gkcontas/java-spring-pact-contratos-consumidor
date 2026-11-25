package com.gkcontas.catalog.contract;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.gkcontas.catalog.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Replays every interaction of every consumer contract against the real application
 * (real controllers, real JPA, real Postgres). One test per interaction, named after
 * the consumer and the interaction description.
 */
@Provider("catalog-service")
@PactFolder("build/consumer-pacts")
public class CatalogProviderPactTest extends IntegrationTestBase {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void target(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", port));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verify(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("a product exists")
    void productExists(Map<String, Object> params) {
        CatalogProviderStates.productExists(jdbc, params);
    }

    @State("a product does not exist")
    void productDoesNotExist(Map<String, Object> params) {
        CatalogProviderStates.productDoesNotExist(jdbc, params);
    }
}
