package dev.supplychain.agent.evaluation.planner;

import dev.supplychain.agent.planning.api.PlanningRequest;

/** Evaluator-only planner seam. It deliberately does not participate in the production PlannerRouter. */
public interface EvaluationPlanner {
    String id();
    EvaluationPlannerResult plan(PlanningRequest request);
}
