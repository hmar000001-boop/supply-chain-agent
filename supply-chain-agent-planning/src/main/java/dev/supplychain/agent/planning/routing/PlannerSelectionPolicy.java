package dev.supplychain.agent.planning.routing;

import dev.supplychain.agent.planning.api.PlanningComplexity;
import dev.supplychain.agent.planning.api.PlannerBudget;

@FunctionalInterface
public interface PlannerSelectionPolicy {
    PlannerSelection select(PlanningComplexity complexity, PlannerBudget budget);
}
