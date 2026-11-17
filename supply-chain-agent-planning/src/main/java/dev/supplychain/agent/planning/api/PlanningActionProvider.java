package dev.supplychain.agent.planning.api;

import java.util.List;

/** Supplies trusted, currently applicable actions for one simulated state. Results must be deterministic for a versioned request/state. */
@FunctionalInterface
public interface PlanningActionProvider {
    List<PlanningAction> availableActions(PlanningRequest request, PlanningState state);
}
