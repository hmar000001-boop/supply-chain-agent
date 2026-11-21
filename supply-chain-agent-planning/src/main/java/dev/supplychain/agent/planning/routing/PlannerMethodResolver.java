package dev.supplychain.agent.planning.routing;

import dev.supplychain.agent.mechanisms.b4.RecoveryMethodReference;
import dev.supplychain.agent.planning.api.PlannerId;

import java.util.Locale;
import java.util.Objects;

/** Resolves a persisted B4 method identifier to one registered M5 planner strategy. */
@FunctionalInterface
public interface PlannerMethodResolver {
    PlannerId resolve(RecoveryMethodReference method);

    /**
     * Canonical mapping used by the reference supply-chain agent. Hosts with domain-specific
     * method identifiers must inject an explicit resolver; an unknown method is never ignored.
     */
    static PlannerMethodResolver canonical() {
        return method -> {
            Objects.requireNonNull(method, "method");
            String normalized = method.methodId().trim().toLowerCase(Locale.ROOT)
                    .replace('_', '-').replace(' ', '-');
            return switch (normalized) {
                case "cot", "cot-sequential", "cot-sequential-planner" -> PlannerId.COT_SEQUENTIAL;
                case "tot", "tot-bounded-search", "tot-search" -> PlannerId.TOT_BOUNDED_SEARCH;
                case "mcts", "rap-inspired-mcts", "rap-mcts" -> PlannerId.RAP_INSPIRED_MCTS;
                default -> throw new IllegalArgumentException(
                        "B4 method is not mapped to a registered planner strategy: " + method.methodId());
            };
        };
    }
}
