package com.gkcontas.catalog.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;

/**
 * Changes a provider team could make to the catalog API, applied to real responses by
 * {@link ResponseMutationFilter}. {@code breaks} is the set of consumers that would
 * actually stop working: it is derived from what each consumer's code reads, and the
 * change matrix asserts that Pact reaches the same verdict.
 */
public enum ResponseMutation {

    ADD_CATEGORY(Endpoint.PRODUCT, Set.of(), "product gains a new field 'category'") {
        @Override
        int apply(ObjectNode body, int status) {
            body.put("category", "peripherals");
            return status;
        }
    },
    REMOVE_DESCRIPTION(Endpoint.PRODUCT, Set.of(), "product loses 'description' (nobody reads it)") {
        @Override
        int apply(ObjectNode body, int status) {
            body.remove("description");
            return status;
        }
    },
    RENAME_STOCK(Endpoint.PRODUCT, Set.of(), "product 'stock' renamed to 'availableStock'") {
        @Override
        int apply(ObjectNode body, int status) {
            rename(body, "stock", "availableStock");
            return status;
        }
    },
    REMOVE_WEIGHT(Endpoint.PRODUCT, Set.of("shipping-service"), "product loses 'weightGrams'") {
        @Override
        int apply(ObjectNode body, int status) {
            body.remove("weightGrams");
            return status;
        }
    },
    RENAME_PRICE(Endpoint.PRODUCT, Set.of("order-service"), "product 'price' renamed to 'unitPrice'") {
        @Override
        int apply(ObjectNode body, int status) {
            rename(body, "price", "unitPrice");
            return status;
        }
    },
    PRICE_AS_STRING(Endpoint.PRODUCT, Set.of("order-service"), "product 'price' sent as a string") {
        @Override
        int apply(ObjectNode body, int status) {
            body.put("price", body.get("price").asText());
            return status;
        }
    },
    NOT_FOUND_WITHOUT_TITLE(Endpoint.NOT_FOUND, Set.of(), "404 problem loses 'title'") {
        @Override
        int apply(ObjectNode body, int status) {
            body.remove("title");
            return status;
        }
    },
    RESERVATION_WITHOUT_REMAINING(Endpoint.RESERVED, Set.of(), "reservation loses 'remainingStock'") {
        @Override
        int apply(ObjectNode body, int status) {
            body.remove("remainingStock");
            return status;
        }
    },
    CONFLICT_WITH_TRACE_ID(Endpoint.CONFLICT, Set.of(), "409 problem gains 'traceId'") {
        @Override
        int apply(ObjectNode body, int status) {
            body.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
            return status;
        }
    },
    CONFLICT_AS_422(Endpoint.CONFLICT, Set.of("order-service"), "insufficient stock answered with 422") {
        @Override
        int apply(ObjectNode body, int status) {
            body.put("status", 422);
            return 422;
        }
    },
    ERROR_CODE_RENAMED(Endpoint.CONFLICT, Set.of("order-service"), "'INSUFFICIENT_STOCK' renamed to 'OUT_OF_STOCK'") {
        @Override
        int apply(ObjectNode body, int status) {
            body.put("code", "OUT_OF_STOCK");
            return status;
        }
    };

    public static final String PROPERTY = "catalog.mutation";

    public enum Endpoint {
        PRODUCT, NOT_FOUND, RESERVED, CONFLICT;

        static Endpoint of(String method, String path, int status) {
            if ("GET".equals(method) && path.startsWith("/api/products/")) {
                return status == 404 ? NOT_FOUND : PRODUCT;
            }
            if ("POST".equals(method) && path.equals("/api/stock-reservations")) {
                return status == 409 ? CONFLICT : RESERVED;
            }
            return null;
        }
    }

    private final Endpoint endpoint;
    private final Set<String> breaks;
    private final String description;

    ResponseMutation(Endpoint endpoint, Set<String> breaks, String description) {
        this.endpoint = endpoint;
        this.breaks = breaks;
        this.description = description;
    }

    public Endpoint endpoint() {
        return endpoint;
    }

    public Set<String> breaks() {
        return breaks;
    }

    public String description() {
        return description;
    }

    /** Mutates the body in place and returns the status to send. */
    abstract int apply(ObjectNode body, int status);

    public static ResponseMutation active() {
        String value = System.getProperty(PROPERTY);
        return value == null ? null : valueOf(value);
    }

    private static void rename(ObjectNode body, String from, String to) {
        JsonNode value = body.remove(from);
        body.set(to, value);
    }
}
