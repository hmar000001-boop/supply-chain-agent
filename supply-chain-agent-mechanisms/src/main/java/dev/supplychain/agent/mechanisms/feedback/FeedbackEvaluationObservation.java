package dev.supplychain.agent.mechanisms.feedback;

import java.util.Objects;

/** Paired holdout result bound to an immutable scenario-input unit and two independent run records. */
public record FeedbackEvaluationObservation(String scenarioId, String experimentUnitFingerprint,
        String baselineRunFingerprint, String candidateRunFingerprint, double baselineUtility, double candidateUtility,
        int baselineSafetyViolations, int candidateSafetyViolations, double baselineResourceUnits, double candidateResourceUnits) {
    public FeedbackEvaluationObservation {
        scenarioId=required(scenarioId,"scenarioId");
        experimentUnitFingerprint=fingerprint(experimentUnitFingerprint,"experimentUnitFingerprint");
        baselineRunFingerprint=fingerprint(baselineRunFingerprint,"baselineRunFingerprint");
        candidateRunFingerprint=fingerprint(candidateRunFingerprint,"candidateRunFingerprint");
        if(baselineRunFingerprint.equals(candidateRunFingerprint))
            throw new IllegalArgumentException("baseline and candidate must have distinct source-run fingerprints");
        if(!Double.isFinite(baselineUtility)||!Double.isFinite(candidateUtility))throw new IllegalArgumentException("utility must be finite");
        if(baselineSafetyViolations<0||candidateSafetyViolations<0)throw new IllegalArgumentException("safety violations cannot be negative");
        if(!Double.isFinite(baselineResourceUnits)||baselineResourceUnits<0||!Double.isFinite(candidateResourceUnits)||candidateResourceUnits<0)
            throw new IllegalArgumentException("resource units must be finite and nonnegative");
    }
    public double utilityDelta(){return candidateUtility-baselineUtility;}
    public double resourceDelta(){return candidateResourceUnits-baselineResourceUnits;}
    public int safetyRegression(){return Math.max(0,candidateSafetyViolations-baselineSafetyViolations);}
    private static String fingerprint(String value,String name){String s=required(value,name);if(!s.matches("[0-9a-f]{64}"))throw new IllegalArgumentException(name+" must be SHA-256");return s;}
    private static String required(String value,String name){String s=Objects.requireNonNull(value,name).trim();if(s.isEmpty())throw new IllegalArgumentException(name+" cannot be blank");return s;}
}
