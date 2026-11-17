package dev.supplychain.agent.planning.api;

import dev.supplychain.agent.domain.common.Money;
import java.util.Objects;

/** Explicit normalized utility weights; this is a policy, not a learned or universal objective function. */
public record PlanningScoringProfile(
        double serviceWeight,
        double costWeight,
        double timelinessWeight,
        double riskWeight,
        double robustnessWeight,
        Money costNormalizationMaximum) {
    public PlanningScoringProfile {
        double[] values = {serviceWeight, costWeight, timelinessWeight, riskWeight, robustnessWeight};
        double sum = 0.0;
        for (double value : values) {
            if (!Double.isFinite(value) || value < 0.0) throw new IllegalArgumentException("scoring weights must be finite and nonnegative");
            sum += value;
        }
        if (Math.abs(sum - 1.0) > 1.0e-9) throw new IllegalArgumentException("scoring weights must sum to 1.0");
        costNormalizationMaximum = Objects.requireNonNull(costNormalizationMaximum, "costNormalizationMaximum");
        if (costNormalizationMaximum.amount().signum() <= 0) throw new IllegalArgumentException("costNormalizationMaximum must be positive");
    }

    public static PlanningScoringProfile balancedCad(String maximumCost) {
        return new PlanningScoringProfile(0.40, 0.15, 0.20, 0.15, 0.10, Money.of(maximumCost, "CAD"));
    }
}
