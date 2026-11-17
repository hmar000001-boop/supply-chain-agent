package dev.supplychain.agent.planning.cot;

import dev.supplychain.agent.planning.api.PlanningAction;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningState;
import java.util.List;

/** Model-backed implementation is supplied through LangChain4j in M6; deterministic providers support tests. */
@FunctionalInterface
public interface SequentialDecisionProvider {
    SequentialDecision chooseNext(PlanningRequest request, PlanningState state, List<PlanningAction> currentlyAvailable);
    default boolean modelBacked() { return false; }
}
