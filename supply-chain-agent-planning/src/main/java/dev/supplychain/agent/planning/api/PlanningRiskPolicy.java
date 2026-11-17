package dev.supplychain.agent.planning.api;

/** Deterministic acceptance threshold; risk estimates are supplied by trusted planning adapters. */
public record PlanningRiskPolicy(double maximumAcceptedRisk, boolean targetDateIsHard) {
    public PlanningRiskPolicy {
        if (!Double.isFinite(maximumAcceptedRisk) || maximumAcceptedRisk < 0.0 || maximumAcceptedRisk > 1.0) {
            throw new IllegalArgumentException("maximumAcceptedRisk must be in [0,1]");
        }
    }
}
