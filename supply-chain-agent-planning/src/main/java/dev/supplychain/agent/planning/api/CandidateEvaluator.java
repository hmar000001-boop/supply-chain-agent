package dev.supplychain.agent.planning.api;

/** Evaluators must be deterministic for an identical request and replayed state. */
public interface CandidateEvaluator {
    /** Stable semantic version of all scoring and hard-constraint evaluation rules. */
    String configurationFingerprint();

    PlanEvaluation evaluate(PlanningRequest request, PlanningState state);
}
