package dev.supplychain.agent.actions.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Trusted execution metadata. Models must never construct or widen this context. */
public record ActionContext(
        String executionId,
        String correlationId,
        ActionPrincipal principal,
        Instant now,
        String idempotencyKey,
        String operationId) {
    public ActionContext {
        executionId = require(executionId, "executionId");
        correlationId = require(correlationId, "correlationId");
        principal = Objects.requireNonNull(principal, "principal");
        now = Objects.requireNonNull(now, "now");
        idempotencyKey = normalizeOptional(idempotencyKey);
        operationId = normalizeOptional(operationId);
    }
    public static ActionContext create(String executionId, String correlationId, ActionPrincipal principal,
                                       Instant now, String idempotencyKey) {
        return new ActionContext(executionId, correlationId, principal, now, idempotencyKey, null);
    }
    public ActionContext withOperationId(String value) {
        return new ActionContext(executionId, correlationId, principal, now, idempotencyKey, value);
    }
    public Optional<String> idempotencyKeyOptional() { return Optional.ofNullable(idempotencyKey); }
    public Optional<String> operationIdOptional() { return Optional.ofNullable(operationId); }
    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
