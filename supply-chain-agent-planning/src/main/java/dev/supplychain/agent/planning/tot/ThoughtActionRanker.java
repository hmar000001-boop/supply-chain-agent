package dev.supplychain.agent.planning.tot;

import dev.supplychain.agent.planning.api.PlanningAction;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningState;
import java.util.List;

/** Supplies bounded, structured successor proposals; it is not itself the ToT search algorithm. */
public interface ThoughtActionRanker {
    /** Stable semantic version of action proposal ordering/generation rules. */
    String configurationFingerprint();

    List<PlanningAction> rank(PlanningRequest request, PlanningState state, List<PlanningAction> availableActions);
}
