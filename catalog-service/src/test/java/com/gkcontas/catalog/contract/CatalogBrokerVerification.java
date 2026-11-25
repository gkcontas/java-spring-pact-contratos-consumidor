package com.gkcontas.catalog.contract;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.junitsupport.loader.PactBrokerConsumerVersionSelectors;
import au.com.dius.pact.provider.junitsupport.loader.SelectorBuilder;
import com.gkcontas.catalog.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The verification a provider pipeline runs: contracts come from the broker, results go
 * back to it. Enabled only when a broker URL is given, e.g.
 *
 * <pre>
 * ./gradlew :catalog-service:test --tests '*CatalogBrokerVerification' \
 *     -Dpactbroker.url=http://localhost:9292 -Dpact.provider.version=$GIT_SHA \
 *     -Dpact.provider.branch=main -Dpact.verifier.publishResults=true
 * </pre>
 *
 * <p>Pending pacts: a contract the provider has never verified on its main branch can
 * fail without failing this build — the consumer cannot break the provider's pipeline
 * by publishing an expectation. WIP pacts: feature-branch contracts are pulled in too,
 * so the consumer learns early whether the provider already satisfies them.
 */
@Provider("catalog-service")
@PactBroker(
        url = "${pactbroker.url}",
        providerBranch = "${pact.provider.branch}",
        enablePendingPacts = "${pactbroker.enablePending:true}",
        includeWipPactsSince = "${pactbroker.includeWipPactsSince:2026-01-01}")
@EnabledIfSystemProperty(named = "pactbroker.url", matches = ".+")
public class CatalogBrokerVerification extends IntegrationTestBase {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    /**
     * What the provider must keep satisfying: every consumer's main branch (what is
     * about to ship) and every consumer version deployed somewhere (what is live).
     */
    @PactBrokerConsumerVersionSelectors
    public static SelectorBuilder consumerVersionSelectors() {
        return new SelectorBuilder().mainBranch().deployedOrReleased();
    }

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
