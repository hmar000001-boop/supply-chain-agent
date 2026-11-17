package dev.supplychain.agent.planning.routing;

import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningResult;
import dev.supplychain.agent.planning.api.PlanningStrategy;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Selects a planner; it does not evaluate, authorize or execute the returned plan. */
public final class PlannerRouter {
    private final Map<PlannerId, PlanningStrategy> strategies;
    private final PlannerSelectionPolicy selectionPolicy;

    public PlannerRouter(List<PlanningStrategy> strategies, PlannerSelectionPolicy selectionPolicy) {
        Objects.requireNonNull(strategies, "strategies");
        var indexed = new EnumMap<PlannerId, PlanningStrategy>(PlannerId.class);
        for (PlanningStrategy strategy : strategies) {
            Objects.requireNonNull(strategy, "strategy");
            if (indexed.putIfAbsent(strategy.id(), strategy) != null) throw new IllegalArgumentException("duplicate planner strategy: " + strategy.id());
        }
        for (PlannerId required : PlannerId.values()) {
            if (!indexed.containsKey(required)) throw new IllegalArgumentException("missing required planner strategy: " + required);
        }
        this.strategies = Map.copyOf(indexed);
        this.selectionPolicy = Objects.requireNonNull(selectionPolicy, "selectionPolicy");
    }

    public PlannerSelection select(PlanningRequest request) {
        Objects.requireNonNull(request, "request");
        return selectionPolicy.select(request.complexity(), request.budget());
    }

    public PlanningResult plan(PlanningRequest request) {
        PlannerSelection selection = select(request);
        PlanningStrategy strategy = strategies.get(selection.plannerId());
        PlanningResult result = invoke(strategy, request);
        return new PlanningResult(result.plannerId(), result.candidates(), result.terminationReason(),
                result.statistics(), result.sessionId(), selection.explanation(), result.trace());
    }

    public PlanningResult planUsing(PlanningIdOverride override, PlanningRequest request) {
        Objects.requireNonNull(override, "override");
        Objects.requireNonNull(request, "request");
        PlanningResult result = invoke(strategies.get(override.plannerId()), request);
        return new PlanningResult(result.plannerId(), result.candidates(), result.terminationReason(),
                result.statistics(), result.sessionId(), "Explicit planner selection: " + override.reason(), result.trace());
    }

    private static PlanningResult invoke(PlanningStrategy strategy, PlanningRequest request) {
        PlanningResult result = Objects.requireNonNull(strategy.plan(request), "planner result");
        if (result.plannerId() != strategy.id()) {
            throw new IllegalStateException("planner result identity does not match registered strategy " + strategy.id());
        }
        return result;
    }

    public record PlanningIdOverride(PlannerId plannerId, String reason) {
        public PlanningIdOverride {
            plannerId = Objects.requireNonNull(plannerId, "plannerId");
            reason = Objects.requireNonNull(reason, "reason").trim();
            if (reason.isEmpty()) throw new IllegalArgumentException("reason cannot be blank");
        }
    }
}
