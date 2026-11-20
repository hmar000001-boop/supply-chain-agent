package dev.supplychain.agent.mechanisms.feedback;

import java.util.Objects;

/** Preregistered holdout criteria; changing thresholds creates a new protocol/version. */
public record FeedbackEvaluationThreshold(String protocolId,int minimumHoldoutScenarios,
        double minimumMeanUtilityGain,double maximumMeanResourceIncrease,int maximumSafetyRegressions) {
    public FeedbackEvaluationThreshold {
        protocolId=Objects.requireNonNull(protocolId,"protocolId").trim();if(protocolId.isEmpty())throw new IllegalArgumentException("protocolId cannot be blank");
        if(minimumHoldoutScenarios<2)throw new IllegalArgumentException("minimumHoldoutScenarios must be at least 2");
        if(!Double.isFinite(minimumMeanUtilityGain)||minimumMeanUtilityGain<0
                ||!Double.isFinite(maximumMeanResourceIncrease)||maximumMeanResourceIncrease<0||maximumSafetyRegressions<0)
            throw new IllegalArgumentException("invalid preregistered threshold");
    }
}
