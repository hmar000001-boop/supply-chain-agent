package dev.supplychain.agent.actions.api;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Stable operation receipt; it does not claim that an external system committed atomically. */
public record ActionReceipt(ActionId actionId, String operationId, String principalId,
                            String correlationId, Instant startedAt, Instant completedAt,
                            String resultReference, Map<String, String> attributes) {
    public ActionReceipt {
        actionId = Objects.requireNonNull(actionId, "actionId");
        operationId = require(operationId, "operationId");
        principalId = require(principalId, "principalId");
        correlationId = require(correlationId, "correlationId");
        startedAt = Objects.requireNonNull(startedAt, "startedAt");
        completedAt = Objects.requireNonNull(completedAt, "completedAt");
        if (completedAt.isBefore(startedAt)) throw new IllegalArgumentException("completedAt precedes startedAt");
        resultReference = resultReference == null || resultReference.isBlank() ? null : resultReference.trim();
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
    }
    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
