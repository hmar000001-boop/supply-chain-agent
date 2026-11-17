package dev.supplychain.agent.planning.api;

import java.time.Duration;
import java.util.Objects;

public record PlanningStatistics(long expansions, long evaluations, long modelCalls,
                                 long rollouts, long prunedBranches, long backtracks,
                                 Duration elapsed) {
    public PlanningStatistics {
        if (expansions < 0 || evaluations < 0 || modelCalls < 0 || rollouts < 0 || prunedBranches < 0 || backtracks < 0) {
            throw new IllegalArgumentException("planning counters cannot be negative");
        }
        elapsed = Objects.requireNonNull(elapsed, "elapsed");
        if (elapsed.isNegative()) throw new IllegalArgumentException("elapsed cannot be negative");
    }
}
