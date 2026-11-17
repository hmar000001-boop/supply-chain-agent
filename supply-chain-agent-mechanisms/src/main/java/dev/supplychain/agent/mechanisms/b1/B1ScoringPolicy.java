package dev.supplychain.agent.mechanisms.b1;

/** Penalty rates convert latency into downstream-utility points. */
public record B1ScoringPolicy(double minimumAccessibilityProbability,
                              double cognitiveLatencyPenaltyPerMillisecond,
                              double infrastructureLatencyPenaltyPerMillisecond,
                              double minimumNetUtility) {
    public B1ScoringPolicy {
        if (!unit(minimumAccessibilityProbability)) throw new IllegalArgumentException("minimum accessibility probability must be in [0,1]");
        if (!finiteNonNegative(cognitiveLatencyPenaltyPerMillisecond)
                || !finiteNonNegative(infrastructureLatencyPenaltyPerMillisecond)
                || !Double.isFinite(minimumNetUtility)) throw new IllegalArgumentException("invalid B1 scoring policy");
    }
    private static boolean unit(double v) { return Double.isFinite(v) && v >= 0.0 && v <= 1.0; }
    private static boolean finiteNonNegative(double v) { return Double.isFinite(v) && v >= 0.0; }
}
