package dev.supplychain.agent.planning.api;

import dev.supplychain.agent.domain.common.Money;
import java.time.Duration;
import java.util.Objects;

/** Trusted, versioned action estimate supplied by an application action proposer. */
public record ActionImpactEstimate(double fulfillmentProgressDelta, Money estimatedCost,
                                   Duration expectedDuration, double risk, double robustness) {
    public ActionImpactEstimate {
        if (!Double.isFinite(fulfillmentProgressDelta) || fulfillmentProgressDelta < 0 || fulfillmentProgressDelta > 1) {
            throw new IllegalArgumentException("fulfillmentProgressDelta must be in [0,1]");
        }
        estimatedCost = Objects.requireNonNull(estimatedCost, "estimatedCost");
        expectedDuration = Objects.requireNonNull(expectedDuration, "expectedDuration");
        if (expectedDuration.isNegative()) throw new IllegalArgumentException("expectedDuration cannot be negative");
        if (!Double.isFinite(risk) || risk < 0 || risk > 1 || !Double.isFinite(robustness) || robustness < 0 || robustness > 1) {
            throw new IllegalArgumentException("risk and robustness must be in [0,1]");
        }
    }
}
