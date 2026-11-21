package dev.supplychain.agent.mechanisms.b3;

/** Paired independent holdout runs for the baseline and one exact candidate coalition policy. */
public record B3CoalitionFeedbackObservation(String scenarioId,String experimentUnitFingerprint,
        String baselineRunFingerprint,String candidateRunFingerprint,double baselineUtility,double candidateUtility,
        int baselineSafetyViolations,int candidateSafetyViolations,double baselineResourceUnits,double candidateResourceUnits) {
    public B3CoalitionFeedbackObservation {
        scenarioId=req(scenarioId,"scenarioId");sha(experimentUnitFingerprint,"experimentUnitFingerprint");
        sha(baselineRunFingerprint,"baselineRunFingerprint");sha(candidateRunFingerprint,"candidateRunFingerprint");
        if(baselineRunFingerprint.equals(candidateRunFingerprint))throw new IllegalArgumentException("paired run fingerprints must differ");
        if(!Double.isFinite(baselineUtility)||!Double.isFinite(candidateUtility)||baselineSafetyViolations<0||candidateSafetyViolations<0
                ||!Double.isFinite(baselineResourceUnits)||!Double.isFinite(candidateResourceUnits)
                ||baselineResourceUnits<0||candidateResourceUnits<0)throw new IllegalArgumentException("invalid paired holdout metrics");
    }
    private static String req(String v,String n){String s=java.util.Objects.requireNonNull(v,n).trim();if(s.isEmpty())throw new IllegalArgumentException(n+" cannot be blank");return s;}
    private static void sha(String v,String n){if(v==null||!v.matches("[0-9a-f]{64}"))throw new IllegalArgumentException(n+" must be SHA-256");}
}
