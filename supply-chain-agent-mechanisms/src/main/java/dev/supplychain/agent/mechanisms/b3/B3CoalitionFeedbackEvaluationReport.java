package dev.supplychain.agent.mechanisms.b3;

import java.time.Instant;
import java.util.Objects;

/**
 * Proof object produced only by {@link B3CoalitionFeedbackPolicyService#evaluate}.
 * Its constructor is intentionally package-private: callers may inspect an evaluation,
 * but cannot manufacture an eligibility decision and pass it to activation.
 */
public final class B3CoalitionFeedbackEvaluationReport {
    public enum Disposition { ELIGIBLE_FOR_AUTHORIZED_ACTIVATION, REJECTED }

    private final String policyId;
    private final long policyVersion;
    private final String candidateFingerprint;
    private final int holdoutScenarioCount;
    private final double meanUtilityGain;
    private final double lowerConfidenceBound95;
    private final int safetyRegressionCount;
    private final double meanResourceIncrease;
    private final Disposition decision;
    private final String reason;
    private final String evaluationFingerprint;
    private final Instant evaluatedAt;

    B3CoalitionFeedbackEvaluationReport(String policyId, long policyVersion, String candidateFingerprint,
            int holdoutScenarioCount, double meanUtilityGain, double lowerConfidenceBound95,
            int safetyRegressionCount, double meanResourceIncrease, Disposition decision, String reason,
            String evaluationFingerprint, Instant evaluatedAt) {
        this.policyId = required(policyId, "policyId");
        if (policyVersion < 1 || holdoutScenarioCount < 0 || safetyRegressionCount < 0)
            throw new IllegalArgumentException("invalid evaluation counts");
        this.policyVersion = policyVersion;
        this.candidateFingerprint = sha(candidateFingerprint, "candidateFingerprint");
        this.holdoutScenarioCount = holdoutScenarioCount;
        if (!Double.isFinite(meanUtilityGain) || !Double.isFinite(lowerConfidenceBound95)
                || !Double.isFinite(meanResourceIncrease))
            throw new IllegalArgumentException("metrics must be finite");
        this.meanUtilityGain = meanUtilityGain;
        this.lowerConfidenceBound95 = lowerConfidenceBound95;
        this.safetyRegressionCount = safetyRegressionCount;
        this.meanResourceIncrease = meanResourceIncrease;
        this.decision = Objects.requireNonNull(decision, "decision");
        this.reason = required(reason, "reason");
        this.evaluationFingerprint = sha(evaluationFingerprint, "evaluationFingerprint");
        this.evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    }

    public String policyId() { return policyId; }
    public long policyVersion() { return policyVersion; }
    public String candidateFingerprint() { return candidateFingerprint; }
    public int holdoutScenarioCount() { return holdoutScenarioCount; }
    public double meanUtilityGain() { return meanUtilityGain; }
    public double lowerConfidenceBound95() { return lowerConfidenceBound95; }
    public int safetyRegressionCount() { return safetyRegressionCount; }
    public double meanResourceIncrease() { return meanResourceIncrease; }
    public Disposition decision() { return decision; }
    public String reason() { return reason; }
    public String evaluationFingerprint() { return evaluationFingerprint; }
    public Instant evaluatedAt() { return evaluatedAt; }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }

    private static String sha(String value, String name) {
        String result = required(value, name);
        if (!result.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(name + " must be SHA-256");
        return result;
    }
}
