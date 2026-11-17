package dev.supplychain.agent.planning.session;

import dev.supplychain.agent.planning.api.PlannerId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Versioned algorithm-specific search state. Durable adapters are introduced in M7. */
public record PlannerSessionCheckpoint(String sessionId, PlannerId plannerId, String requestFingerprint,
                                       String algorithmConfigurationFingerprint, long revision,
                                       PlannerSessionStatus status,
                                       List<PlannerPathCheckpoint> frontier,
                                       List<PlannerNodeCheckpoint> treeNodes,
                                       long expansions, long evaluations, long modelCalls, long rollouts,
                                       long prunedBranches, long backtracks, Duration cumulativeElapsed,
                                       Instant updatedAt) {
    public PlannerSessionCheckpoint {
        sessionId = require(sessionId, "sessionId");
        plannerId = Objects.requireNonNull(plannerId, "plannerId");
        requestFingerprint = require(requestFingerprint, "requestFingerprint");
        algorithmConfigurationFingerprint = require(algorithmConfigurationFingerprint, "algorithmConfigurationFingerprint");
        if (revision < 1 || expansions < 0 || evaluations < 0 || modelCalls < 0 || rollouts < 0
                || prunedBranches < 0 || backtracks < 0) {
            throw new IllegalArgumentException("invalid checkpoint counters");
        }
        status = Objects.requireNonNull(status, "status");
        frontier = List.copyOf(Objects.requireNonNull(frontier, "frontier"));
        treeNodes = List.copyOf(Objects.requireNonNull(treeNodes, "treeNodes"));
        cumulativeElapsed = Objects.requireNonNull(cumulativeElapsed, "cumulativeElapsed");
        if (cumulativeElapsed.isNegative()) throw new IllegalArgumentException("cumulativeElapsed cannot be negative");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }
    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
