package com.gkcontas.catalog.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gkcontas.catalog.IntegrationTestBase;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * For each {@link ResponseMutation}: runs the real Pact verification (the same test
 * class the build runs, through the JUnit launcher) and a schema snapshot of the same
 * four responses — fields, JSON types and status codes, which is what an OpenAPI
 * snapshot captures. Records which consumers Pact says break and whether the snapshot
 * changed, and asserts Pact matches the consumers that would really break.
 */
class ContractChangeMatrixTest extends IntegrationTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    record Row(ResponseMutation mutation, Set<String> pactBreaks, boolean snapshotChanged, long pactMillis) {
    }

    @Test
    void pactFlagsExactlyTheChangesThatBreakAConsumer() throws Exception {
        Set<String> baseline = schemaSnapshot();
        PactRun clean = runPactVerification();
        assertThat(clean.failedConsumers()).as("unchanged provider").isEmpty();
        assertThat(clean.interactions()).isEqualTo(6);

        List<Row> rows = new ArrayList<>();
        for (ResponseMutation mutation : ResponseMutation.values()) {
            System.setProperty(ResponseMutation.PROPERTY, mutation.name());
            try {
                PactRun run = runPactVerification();
                boolean snapshotChanged = !schemaSnapshot().equals(baseline);
                rows.add(new Row(mutation, run.failedConsumers(), snapshotChanged, run.millis()));
            } finally {
                System.clearProperty(ResponseMutation.PROPERTY);
            }
        }

        writeReport(clean, rows);
        for (Row row : rows) {
            assertThat(row.pactBreaks()).as(row.mutation().name()).isEqualTo(row.mutation().breaks());
        }
    }

    record PactRun(int interactions, Set<String> failedConsumers, long millis) {
    }

    private PactRun runPactVerification() {
        Set<String> failed = new TreeSet<>();
        int[] interactions = {0};
        TestExecutionListener listener = new TestExecutionListener() {
            @Override
            public void executionFinished(TestIdentifier id, TestExecutionResult result) {
                if (!id.isTest()) {
                    return;
                }
                interactions[0]++;
                if (result.getStatus() != TestExecutionResult.Status.SUCCESSFUL) {
                    failed.add(id.getDisplayName().split(" - ", 2)[0]);
                }
            }
        };
        Launcher launcher = LauncherFactory.create();
        long start = System.nanoTime();
        launcher.execute(LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(CatalogProviderPactTest.class))
                .build(), listener);
        long millis = (System.nanoTime() - start) / 1_000_000;
        return new PactRun(interactions[0], failed, millis);
    }

    /** status + every JSON path with its node type, for the four contract responses. */
    private Set<String> schemaSnapshot() throws IOException, InterruptedException {
        CatalogProviderStates.productExists(jdbc, Map.of("sku", "KB-001", "stock", 10));
        CatalogProviderStates.productExists(jdbc, Map.of("sku", "MN-003", "stock", 1));
        CatalogProviderStates.productDoesNotExist(jdbc, Map.of("sku", "XX-404"));

        Set<String> shape = new TreeSet<>();
        shape(shape, "product", call("GET", "/api/products/KB-001", null));
        shape(shape, "not-found", call("GET", "/api/products/XX-404", null));
        shape(shape, "reserved", call("POST", "/api/stock-reservations",
                "{\"sku\":\"KB-001\",\"quantity\":2,\"orderRef\":\"ORD-snapshot\"}"));
        shape(shape, "conflict", call("POST", "/api/stock-reservations",
                "{\"sku\":\"MN-003\",\"quantity\":5,\"orderRef\":\"ORD-snapshot\"}"));
        return shape;
    }

    private HttpResponse<String> call(String method, String path, String body) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json");
        if (body == null) {
            request.GET();
        } else {
            request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private static void shape(Set<String> shape, String name, HttpResponse<String> response) throws IOException {
        shape.add(name + " status " + response.statusCode());
        JsonNode json = MAPPER.readTree(response.body());
        for (Map.Entry<String, JsonNode> field : json.properties()) {
            shape.add(name + " ." + field.getKey() + ": " + field.getValue().getNodeType());
        }
    }

    private static void writeReport(PactRun clean, List<Row> rows) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append(String.format("baseline: %d interactions verified in %d ms%n%n", clean.interactions(), clean.millis()));
        out.append(String.format("%-30s %-46s %-34s %-9s %s%n", "mutation", "change", "pact: consumers broken",
                "snapshot", "verify ms"));
        int pactFlagged = 0;
        int snapshotFlagged = 0;
        int realBreaks = 0;
        int snapshotMissed = 0;
        int pactMissed = 0;
        int pactFalsePositives = 0;
        for (Row row : rows) {
            boolean breaks = !row.mutation().breaks().isEmpty();
            realBreaks += breaks ? 1 : 0;
            pactFlagged += row.pactBreaks().isEmpty() ? 0 : 1;
            snapshotFlagged += row.snapshotChanged() ? 1 : 0;
            snapshotMissed += breaks && !row.snapshotChanged() ? 1 : 0;
            pactMissed += breaks && row.pactBreaks().isEmpty() ? 1 : 0;
            pactFalsePositives += !breaks && !row.pactBreaks().isEmpty() ? 1 : 0;
            out.append(String.format("%-30s %-46s %-34s %-9s %d%n", row.mutation().name(), row.mutation().description(),
                    row.pactBreaks().isEmpty() ? "-" : String.join(", ", row.pactBreaks()),
                    row.snapshotChanged() ? "CHANGED" : "same", row.pactMillis()));
        }
        out.append(String.format("%nchanges: %d, real breaks: %d%n", rows.size(), realBreaks));
        out.append(String.format("pact flagged: %d (false positives: %d, missed: %d)%n", pactFlagged,
                pactFalsePositives, pactMissed));
        out.append(String.format("snapshot flagged: %d (false positives: %d, missed: %d)%n", snapshotFlagged,
                snapshotFlagged - (realBreaks - snapshotMissed), snapshotMissed));
        Path report = Path.of(System.getProperty("matrix.report", "build/reports/contract-change-matrix.txt"));
        Files.createDirectories(report.getParent());
        Files.writeString(report, out);
        System.out.print(out);
    }
}
