package com.gkcontas.catalog.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gkcontas.catalog.IntegrationTestBase;
import com.gkcontas.catalog.broker.PactBrokerApi.CanIDeploy;
import com.gkcontas.catalog.contract.CatalogBrokerVerification;
import com.gkcontas.catalog.contract.ResponseMutation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * The life of a contract across two teams, against a real Pact Broker:
 *
 * <ol>
 *   <li>consumers publish from main; the provider verifies and publishes results;</li>
 *   <li>can-i-deploy lets the provider go first, then the consumers;</li>
 *   <li>order-service, on a feature branch, starts expecting a field the provider does
 *       not have yet: the provider build stays green (pending/WIP), the consumer is
 *       blocked;</li>
 *   <li>the provider ships the field; the consumer is still blocked until that
 *       provider version is deployed, then it is released;</li>
 *   <li>a provider change that removes a field a deployed consumer reads fails the
 *       provider build and can-i-deploy.</li>
 * </ol>
 *
 * Provider versions are simulated with {@link ResponseMutation}: "c2" is the build that
 * added {@code category}, "c3" the build that dropped {@code weightGrams}.
 */
class PactBrokerWorkflowIT extends IntegrationTestBase {

    private static final String PROVIDER = "catalog-service";
    private static final String PRODUCTION = "production";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // SQLite keeps the broker to one container in tests; the compose file runs it on
    // Postgres, as it would be in any shared environment.
    static final GenericContainer<?> BROKER =
            new GenericContainer<>("pactfoundation/pact-broker:3.2.0-pactbroker2.124.0")
                    .withEnv("PACT_BROKER_DATABASE_ADAPTER", "sqlite")
                    .withEnv("PACT_BROKER_DATABASE_NAME", "/tmp/pact_broker.sqlite3")
                    .withExposedPorts(9292)
                    .waitingFor(Wait.forHttp("/diagnostic/status/heartbeat").forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(2)));

    static {
        BROKER.start();
    }

    private final Map<String, String> answers = new LinkedHashMap<>();
    private final Map<String, Verification> verifications = new LinkedHashMap<>();

    @Test
    void contractLifecycleAcrossTwoTeams() throws IOException {
        String url = "http://" + BROKER.getHost() + ":" + BROKER.getMappedPort(9292);
        PactBrokerApi broker = new PactBrokerApi(url);
        String orderPact = readPact("order-service-catalog-service.json");
        String shippingPact = readPact("shipping-service-catalog-service.json");

        // 1. Consumers publish from main.
        broker.publish("order-service", "o1", "main", PROVIDER, orderPact);
        broker.publish("shipping-service", "s1", "main", PROVIDER, shippingPact);
        // "Unknown" is null, not false: a script that only checks for false would ship.
        assertThat(record("o1 before any verification", broker.canIDeploy("order-service", "o1", PRODUCTION))
                .deployable()).isNull();

        // 2. Provider c1 verifies and goes first; then the consumers.
        Verification c1 = verify(url, "c1", null);
        assertThat(c1.failed()).isZero();
        assertThat(c1.passed()).isEqualTo(6);
        assertThat(record("c1 -> production", broker.canIDeploy(PROVIDER, "c1", PRODUCTION)).deployable()).isTrue();
        broker.recordDeployment(PROVIDER, "c1", PRODUCTION);
        assertThat(record("o1 -> production", broker.canIDeploy("order-service", "o1", PRODUCTION)).deployable())
                .isTrue();
        broker.recordDeployment("order-service", "o1", PRODUCTION);
        assertThat(record("s1 -> production", broker.canIDeploy("shipping-service", "s1", PRODUCTION)).deployable())
                .isTrue();
        broker.recordDeployment("shipping-service", "s1", PRODUCTION);

        // 3. order-service o2, on a feature branch, starts reading 'category'.
        broker.publish("order-service", "o2", "feat/product-category", PROVIDER, withCategory(orderPact));
        assertThat(record("o2 before verification", broker.canIDeploy("order-service", "o2", PRODUCTION))
                .deployable()).isNull();
        Verification c1Again = verify(url, "c1", null);
        assertThat(c1Again.failed()).as("a pending pact must not fail the provider build").isZero();
        assertThat(c1Again.passed()).as("o1 + s1 + the WIP o2 interactions").isEqualTo(10);
        CanIDeploy o2Blocked = record("o2 after c1 failed its WIP pact",
                broker.canIDeploy("order-service", "o2", PRODUCTION));
        assertThat(o2Blocked.deployable()).isFalse();
        assertThat(o2Blocked.failed()).isPositive();

        // 4. Provider c2 adds 'category'. The consumer still waits for c2 to be live.
        Verification c2 = verify(url, "c2", ResponseMutation.ADD_CATEGORY);
        assertThat(c2.failed()).isZero();
        assertThat(record("o2 while c2 verified, not deployed",
                broker.canIDeploy("order-service", "o2", PRODUCTION)).deployable()).isFalse();
        assertThat(record("c2 -> production", broker.canIDeploy(PROVIDER, "c2", PRODUCTION)).deployable()).isTrue();
        broker.recordDeployment(PROVIDER, "c2", PRODUCTION);
        assertThat(record("o2 after c2 deployed", broker.canIDeploy("order-service", "o2", PRODUCTION))
                .deployable()).isTrue();

        // 5. Provider c3 drops 'weightGrams', which deployed shipping s1 reads.
        Verification c3 = verify(url, "c3", ResponseMutation.REMOVE_WEIGHT);
        assertThat(c3.failed()).as("s1 is deployed and on main: its pact is not pending").isPositive();
        assertThat(record("c3 -> production", broker.canIDeploy(PROVIDER, "c3", PRODUCTION)).deployable()).isFalse();

        writeReport();
    }

    record Verification(long passed, long failed, long millis) {
    }

    private Verification verify(String brokerUrl, String providerVersion, ResponseMutation mutation) {
        Map<String, String> properties = Map.of(
                "pactbroker.url", brokerUrl,
                "pact.provider.version", providerVersion,
                "pact.provider.branch", "main",
                "pact.verifier.publishResults", "true");
        properties.forEach(System::setProperty);
        if (mutation != null) {
            System.setProperty(ResponseMutation.PROPERTY, mutation.name());
        }
        try {
            SummaryGeneratingListener listener = new SummaryGeneratingListener();
            long start = System.nanoTime();
            LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                    .selectors(selectClass(CatalogBrokerVerification.class))
                    .build(), listener);
            TestExecutionSummary summary = listener.getSummary();
            // A container failure (no pacts found, a broken selector method) runs zero
            // interactions: counting only failed tests would read it as a green build.
            Verification verification = new Verification(summary.getTestsSucceededCount(),
                    summary.getTestsFailedCount() + summary.getContainersFailedCount(),
                    (System.nanoTime() - start) / 1_000_000);
            String key = verifications.containsKey(providerVersion) ? providerVersion + " again" : providerVersion;
            verifications.put(key, verification);
            return verification;
        } finally {
            properties.keySet().forEach(System::clearProperty);
            System.clearProperty(ResponseMutation.PROPERTY);
        }
    }

    private CanIDeploy record(String step, CanIDeploy answer) {
        answers.put(step, String.format("deployable=%s ok=%d failed=%d unknown=%d | %s",
                answer.deployable(), answer.success(), answer.failed(), answer.unknown(), answer.reason()));
        return answer;
    }

    /** order-service o2 reads one more field: same contract plus 'category' on the product. */
    private static String withCategory(String pactJson) throws IOException {
        ObjectNode pact = (ObjectNode) MAPPER.readTree(pactJson);
        pact.withArray("interactions").forEach(interaction -> {
            if ("a request for an existing product".equals(interaction.path("description").asText())) {
                ObjectNode response = (ObjectNode) interaction.path("response");
                ((ObjectNode) response.path("body").path("content")).put("category", "peripherals");
                ObjectNode rule = response.withObject("matchingRules").withObject("body").putObject("$.category");
                rule.put("combine", "AND").putArray("matchers").addObject().put("match", "type");
            }
        });
        return MAPPER.writeValueAsString(pact);
    }

    private static String readPact(String file) throws IOException {
        return Files.readString(Path.of(System.getProperty("consumer.pacts.dir", "build/consumer-pacts"), file));
    }

    private void writeReport() throws IOException {
        StringBuilder out = new StringBuilder("provider verifications (passed/failed, ms)\n");
        verifications.forEach((name, v) -> out.append(String.format("  %-9s %2d/%d  %5d ms%n", name, v.passed(),
                v.failed(), v.millis())));
        out.append("\ncan-i-deploy --to-environment production\n");
        answers.forEach((step, answer) -> out.append(String.format("  %-36s %s%n", step, answer)));
        Path path = Path.of(System.getProperty("broker.report", "build/reports/broker-workflow.txt"));
        Files.createDirectories(path.getParent());
        Files.writeString(path, out);
        System.out.print(out);
    }
}
