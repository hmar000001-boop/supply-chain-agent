package dev.supplychain.agent.mechanisms.feedback;

import java.util.Objects;

/** Paired, independently held-out result for the frozen baseline and a shadow candidate. */
public record FeedbackEvaluationObservation(String scenarioId,double baselineUtility,double candidateUtility,
        int baselineSafetyViolations,int candidateSafetyViolations,double baselineResourceUnits,double candidateResourceUnits) {
    public FeedbackEvaluationObservation {
        scenarioId=Objects.requireNonNull(scenarioId,"scenarioId").trim();if(scenarioId.isEmpty())throw new IllegalArgumentException("scenarioId cannot be blank");
        if(!Double.isFinite(baselineUtility)||!Double.isFinite(candidateUtility))throw new IllegalArgumentException("utility must be finite");
        if(baselineSafetyViolations<0||candidateSafetyViolations<0)throw new IllegalArgumentException("safety violations cannot be negative");
        if(!Double.isFinite(baselineResourceUnits)||baselineResourceUnits<0||!Double.isFinite(candidateResourceUnits)||candidateResourceUnits<0)
            throw new IllegalArgumentException("resource units must be finite and nonnegative");
    }
    public double utilityDelta(){return candidateUtility-baselineUtility;}
    public double resourceDelta(){return candidateResourceUnits-baselineResourceUnits;}
    public int safetyRegression(){return Math.max(0,candidateSafetyViolations-baselineSafetyViolations);}
}
