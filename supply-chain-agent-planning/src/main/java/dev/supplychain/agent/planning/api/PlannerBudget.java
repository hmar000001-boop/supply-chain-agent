package dev.supplychain.agent.planning.api;

import java.time.Duration;
import java.math.BigInteger;
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

    /**
     * Returns a strictly bounded per-planning-call resource envelope. Structural limits
     * (depth and candidate cardinality) and the absolute deadline are preserved; B6 may
     * reduce only consumable compute ceilings and the call-local runtime allowance.
     */
    public PlannerBudget scaleComputeResources(int scalePercent) {
        if (scalePercent < 1 || scalePercent > 100) {
            throw new IllegalArgumentException("planner compute scale must be between 1 and 100 percent");
        }
        if (scalePercent == 100) return this;
        BigInteger billion = BigInteger.valueOf(1_000_000_000L);
        BigInteger runtimeNanos = BigInteger.valueOf(maxRuntime.getSeconds()).multiply(billion)
                .add(BigInteger.valueOf(maxRuntime.getNano()));
        BigInteger scaledNanos = runtimeNanos.multiply(BigInteger.valueOf(scalePercent))
                .divide(BigInteger.valueOf(100L))
                .max(BigInteger.valueOf(1_000_000L))
                .min(runtimeNanos);
        BigInteger[] secondsAndNanos = scaledNanos.divideAndRemainder(billion);
        Duration scaledRuntime = Duration.ofSeconds(secondsAndNanos[0].longValueExact(),
                secondsAndNanos[1].longValueExact());
        return new PlannerBudget(maxDepth,
                scalePositive(maxExpansions, scalePercent),
                scalePositive(maxEvaluations, scalePercent),
                scaleZeroAllowed(maxModelCalls, scalePercent),
                scaleZeroAllowed(maxRollouts, scalePercent),
                maxCandidates,
                scaledRuntime,
                deadline);
    }

    private static int scalePositive(int value, int scalePercent) {
        return Math.max(1, (int) ((long) value * scalePercent / 100L));
    }

    private static int scaleZeroAllowed(int value, int scalePercent) {
        return (int) ((long) value * scalePercent / 100L);
    }
}
