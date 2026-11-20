package dev.supplychain.agent.planning.api;

import dev.supplychain.agent.mechanisms.b6.ResourceBudgetExceededException;
import dev.supplychain.agent.mechanisms.b6.ResourceUnit;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Per-invocation algorithm counters plus B6 accounting. A context must not be shared across planner runs. */
public final class PlannerExecutionContext {
    private final PlanningRequest request;
    private final PlannerId plannerId;
    private final PlannerResourceMeter meter;
    private final Clock clock;
    private final Instant startedAt;
    private final EnumMap<ResourceUnit, Long> consumed = new EnumMap<>(ResourceUnit.class);
    private PlannerTerminationReason stopReason;
    private long prunedBranches;
    private long backtracks;
    private Duration elapsedBeforeResume = Duration.ZERO;

    public PlannerExecutionContext(PlanningRequest request, PlannerId plannerId,
                                   PlannerResourceMeter meter, Clock clock) {
        this.request = Objects.requireNonNull(request, "request");
        this.plannerId = Objects.requireNonNull(plannerId, "plannerId");
        this.meter = Objects.requireNonNull(meter, "meter");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.startedAt = clock.instant();
        if (!startedAt.isBefore(request.budget().deadline())) stopReason = PlannerTerminationReason.DEADLINE_EXCEEDED;
    }

    public boolean consume(ResourceUsage usage) {
        Objects.requireNonNull(usage, "usage");
        Instant now = clock.instant();
        if (stopReason != null) return false;
        if (!now.isBefore(request.budget().deadline())
                || Duration.between(startedAt, now).compareTo(request.budget().maxRuntime()) >= 0) {
            stopReason = PlannerTerminationReason.DEADLINE_EXCEEDED;
            return false;
        }
        for (ResourceUnit unit : ResourceUnit.values()) {
            long requested = usage.amount(unit);
            long current = consumed.getOrDefault(unit, 0L);
            long maximum = maximum(unit);
            if (requested > 0 && (current > maximum || requested > maximum - current)) {
                stopReason = PlannerTerminationReason.SEARCH_BUDGET_EXHAUSTED;
                return false;
            }
        }
        try {
            meter.record(request.executionId(), plannerId, usage, now);
        } catch (ResourceBudgetExceededException ex) {
            stopReason = PlannerTerminationReason.RESOURCE_BUDGET_EXHAUSTED;
            return false;
        }
        for (ResourceUnit unit : ResourceUnit.values()) {
            long amount = usage.amount(unit);
            consumed.put(unit, Math.addExact(consumed.getOrDefault(unit, 0L), amount));
        }
        return true;
    }

    private long maximum(ResourceUnit unit) {
        return switch (unit) {
            case MODEL_CALLS -> request.budget().maxModelCalls();
            case REASONING_EVALUATIONS, VERIFICATION_CALLS -> request.budget().maxEvaluations();
            case SEARCH_EXPANSIONS -> request.budget().maxExpansions();
            case MCTS_ROLLOUTS -> request.budget().maxRollouts();
            case INPUT_TOKENS, OUTPUT_TOKENS, EXTERNAL_READS, ACTION_DISPATCHES -> Long.MAX_VALUE;
        };
    }

    public void branchPruned() { prunedBranches = Math.addExact(prunedBranches, 1); }
    public void backtracked() { backtracks = Math.addExact(backtracks, 1); }
    public PlannerTerminationReason stopReason() { return stopReason; }
    public long consumed(ResourceUnit unit) { return consumed.getOrDefault(unit, 0L); }
    public Instant now() { return clock.instant(); }
    public String executionId() { return request.executionId(); }

    /** Restores local per-run counters from an algorithm checkpoint; B6 ledger totals are already durable. */
    public void restoreCounters(long expansions, long evaluations, long modelCalls, long rollouts,
                                long restoredPrunedBranches, long restoredBacktracks,
                                Duration restoredElapsed) {
        if (!consumed.isEmpty()) throw new IllegalStateException("planner counters can only be restored before resource consumption");
        if (expansions < 0 || evaluations < 0 || modelCalls < 0 || rollouts < 0
                || restoredPrunedBranches < 0 || restoredBacktracks < 0) {
            throw new IllegalArgumentException("restored counters cannot be negative");
        }
        elapsedBeforeResume = Objects.requireNonNull(restoredElapsed, "restoredElapsed");
        if (elapsedBeforeResume.isNegative()) throw new IllegalArgumentException("restored elapsed time cannot be negative");
        consumed.put(ResourceUnit.SEARCH_EXPANSIONS, expansions);
        consumed.put(ResourceUnit.REASONING_EVALUATIONS, evaluations);
        consumed.put(ResourceUnit.MODEL_CALLS, modelCalls);
        consumed.put(ResourceUnit.MCTS_ROLLOUTS, rollouts);
        prunedBranches = restoredPrunedBranches;
        backtracks = restoredBacktracks;
    }

    public PlanningStatistics statistics() {
        Duration elapsed = Duration.between(startedAt, clock.instant());
        if (elapsed.isNegative()) elapsed = Duration.ZERO;
        try {
            elapsed = elapsedBeforeResume.plus(elapsed);
        } catch (ArithmeticException overflow) {
            elapsed = Duration.ofSeconds(Long.MAX_VALUE);
        }
        return new PlanningStatistics(consumed(ResourceUnit.SEARCH_EXPANSIONS),
                consumed(ResourceUnit.REASONING_EVALUATIONS), consumed(ResourceUnit.MODEL_CALLS),
                consumed(ResourceUnit.MCTS_ROLLOUTS), prunedBranches, backtracks, elapsed);
    }
}
