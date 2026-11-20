package dev.supplychain.agent.mechanisms.feedback;

import java.time.Instant;
import java.util.Objects;

public record FeedbackPolicyEvaluationReport(String policyId,long policyVersion,String thresholdProtocolId,
        int holdoutScenarioCount,double meanUtilityGain,double lowerConfidenceBound95,
        int safetyRegressionCount,double meanResourceIncrease,PolicyDisposition decision,
        String reason,String evaluationFingerprint,Instant evaluatedAt) {
    public FeedbackPolicyEvaluationReport {
        policyId=Objects.requireNonNull(policyId,"policyId");if(policyVersion<1||holdoutScenarioCount<0)throw new IllegalArgumentException("invalid evaluation identity/count");
        thresholdProtocolId=Objects.requireNonNull(thresholdProtocolId,"thresholdProtocolId");
        if(!Double.isFinite(meanUtilityGain)||!Double.isFinite(lowerConfidenceBound95)||safetyRegressionCount<0||!Double.isFinite(meanResourceIncrease))
            throw new IllegalArgumentException("evaluation metrics must be finite");
        decision=Objects.requireNonNull(decision,"decision");reason=Objects.requireNonNull(reason,"reason");
        evaluationFingerprint=Objects.requireNonNull(evaluationFingerprint,"evaluationFingerprint");
        if(!evaluationFingerprint.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("evaluationFingerprint must be SHA-256");
        evaluatedAt=Objects.requireNonNull(evaluatedAt,"evaluatedAt");
    }
}
