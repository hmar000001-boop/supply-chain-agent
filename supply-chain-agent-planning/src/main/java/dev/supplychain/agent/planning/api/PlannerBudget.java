package dev.supplychain.agent.planning.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Algorithmic upper bounds. B6 separately enforces the shared execution resource ledger. */
public record PlannerBudget(
        int maxDepth,
        int maxExpansions,
        int maxEvaluations,
        int maxModelCalls,
        int maxRollouts,
        int maxCandidates,
        Duration maxRuntime,
        Instant deadline) {
    public PlannerBudget {
        if (maxDepth < 1 || maxExpansions < 1 || maxEvaluations < 1 || maxModelCalls < 0
                || maxRollouts < 0 || maxCandidates < 1) {
            throw new IllegalArgumentException("planner limits are outside their valid ranges");
        }
        maxRuntime = Objects.requireNonNull(maxRuntime, "maxRuntime");
        deadline = Objects.requireNonNull(deadline, "deadline");
        if (maxRuntime.isZero() || maxRuntime.isNegative()) throw new IllegalArgumentException("maxRuntime must be positive");
    }
}
