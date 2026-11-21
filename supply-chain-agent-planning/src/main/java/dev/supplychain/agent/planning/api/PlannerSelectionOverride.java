package dev.supplychain.agent.planning.api;

import java.util.Objects;

/**
 * Auditable B4 method binding carried by the immutable planning request.
 * The candidate reference records the intention's method provenance; it is not an executable
 * plan and never bypasses candidate evaluation, approval, revalidation or action authorization.
 */
public record PlannerSelectionOverride(
        PlannerId plannerId,
        String methodId,
        String candidatePlanReference,
        String intentionId,
        long intentionVersion,
        String objectiveFingerprint,
        String reason) {
    public PlannerSelectionOverride {
        plannerId = Objects.requireNonNull(plannerId, "plannerId");
        methodId = required(methodId, "methodId");
        candidatePlanReference = required(candidatePlanReference, "candidatePlanReference");
        intentionId = required(intentionId, "intentionId");
        if (intentionVersion < 0) throw new IllegalArgumentException("intentionVersion cannot be negative");
        objectiveFingerprint = required(objectiveFingerprint, "objectiveFingerprint");
        if (!objectiveFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("objectiveFingerprint must be lowercase SHA-256 hex");
        }
        reason = required(reason, "reason");
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
