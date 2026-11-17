package dev.supplychain.agent.planning.api;

import java.util.List;

/** Domain-grounded search interface. Implementations must be deterministic and must not perform external side effects. */
public interface PlanningWorldModel {
    /** Stable semantic version of the transition/action-generation rules used by this model. */
    String configurationFingerprint();

    PlanningState initialState(PlanningRequest request);
    List<PlanningAction> availableActions(PlanningRequest request, PlanningState state);
    WorldTransition apply(PlanningRequest request, PlanningState state, PlanningAction action);
}
