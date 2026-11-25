package com.gkcontas.catalog.broker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * The handful of broker endpoints a pipeline calls. In CI these are the pact-broker CLI
 * commands {@code publish}, {@code can-i-deploy} and {@code record-deployment}; here
 * they are plain HTTP so the test shows what each one actually asks the broker.
 */
public class PactBrokerApi {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient http = HttpClient.newHttpClient();

    public PactBrokerApi(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** {@code deployable} is null when the broker cannot tell (no verification yet). */
    public record CanIDeploy(Boolean deployable, String reason, int success, int failed, int unknown) {
    }

    public void publish(String consumer, String version, String branch, String provider, String pactJson) {
        ObjectNode body = MAPPER.createObjectNode()
                .put("pacticipantName", consumer)
                .put("pacticipantVersionNumber", version)
                .put("branch", branch);
        ArrayNode contracts = body.putArray("contracts");
        contracts.addObject()
                .put("consumerName", consumer)
                .put("providerName", provider)
                .put("specification", "pact")
                .put("contentType", "application/json")
                .put("content", Base64.getEncoder().encodeToString(pactJson.getBytes(StandardCharsets.UTF_8)));
        send("POST", "/contracts/publish", body.toString());
    }

    public CanIDeploy canIDeploy(String pacticipant, String version, String environment) {
        JsonNode summary = send("GET", "/can-i-deploy?pacticipant=" + encode(pacticipant)
                + "&version=" + encode(version) + "&environment=" + encode(environment), null).path("summary");
        JsonNode deployable = summary.path("deployable");
        return new CanIDeploy(deployable.isBoolean() ? deployable.asBoolean() : null,
                summary.path("reason").asText(), summary.path("success").asInt(),
                summary.path("failed").asInt(), summary.path("unknown").asInt());
    }

    public void recordDeployment(String pacticipant, String version, String environment) {
        String uuid = null;
        for (JsonNode env : send("GET", "/environments", null).path("_embedded").path("environments")) {
            if (environment.equals(env.path("name").asText())) {
                uuid = env.path("uuid").asText();
            }
        }
        if (uuid == null) {
            throw new IllegalStateException("Unknown environment " + environment);
        }
        send("POST", "/pacticipants/" + encode(pacticipant) + "/versions/" + encode(version)
                + "/deployed-versions/environment/" + uuid, "{}");
    }

    private JsonNode send(String method, String path, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Accept", "application/hal+json");
        if (body == null) {
            request.GET();
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException(method + " " + path + " -> " + response.statusCode() + ": "
                        + response.body());
            }
            return response.body().isBlank() ? MAPPER.createObjectNode() : MAPPER.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
