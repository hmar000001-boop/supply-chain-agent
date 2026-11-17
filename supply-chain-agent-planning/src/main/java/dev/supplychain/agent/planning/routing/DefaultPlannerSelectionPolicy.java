package dev.supplychain.agent.planning.routing;

import dev.supplychain.agent.planning.api.PlanningComplexity;
import dev.supplychain.agent.planning.api.PlannerBudget;
import dev.supplychain.agent.planning.api.PlannerId;
import java.util.Objects;

/** Explainable routing thresholds; applications may replace this policy with a validated domain policy. */
public final class DefaultPlannerSelectionPolicy implements PlannerSelectionPolicy {
    @Override
    public PlannerSelection select(PlanningComplexity complexity, PlannerBudget budget) {
        Objects.requireNonNull(complexity, "complexity");
        Objects.requireNonNull(budget, "budget");
        if (complexity.expectedBranchingFactor() <= 2 && complexity.expectedHorizon() <= 2
                && complexity.uncertainty() <= 0.25 && complexity.consequenceSeverity() <= 0.50) {
            return new PlannerSelection(PlannerId.COT_SEQUENTIAL,
                    "Low branching, short horizon, low uncertainty and moderate-or-lower consequence favor the sequential baseline.");
        }
        if (complexity.expectedBranchingFactor() <= 6 && complexity.expectedHorizon() <= 5
                && budget.maxExpansions() >= 4) {
            return new PlannerSelection(PlannerId.TOT_BOUNDED_SEARCH,
                    "Moderate branching and horizon justify bounded alternative-path exploration with beam pruning.");
        }
        if (budget.maxRollouts() > 0) {
            return new PlannerSelection(PlannerId.RAP_INSPIRED_MCTS,
                    "Larger branching/horizon or elevated uncertainty justify budgeted MCTS exploration over the simulator.");
        }
        return new PlannerSelection(PlannerId.TOT_BOUNDED_SEARCH,
                "MCTS rollout budget is unavailable; use bounded ToT search as the remaining alternative-exploration strategy.");
    }
}
