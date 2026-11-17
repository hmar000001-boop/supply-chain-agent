package dev.supplychain.agent.mechanisms.b1;

import java.time.Duration;
import java.util.Objects;

/** Configurable recency/frequency plus cue-association estimate; an engineering approximation, not a paper-exact formula. */
public final class RecencyFrequencyActivationEstimator implements ActivationEstimator {
    private final double decay;
    private final double associationWeight;
    private final double emptyHistoryActivation;
    public RecencyFrequencyActivationEstimator(double decay, double associationWeight) { this(decay, associationWeight, -2.0); }
    public RecencyFrequencyActivationEstimator(double decay, double associationWeight, double emptyHistoryActivation) {
        if (!Double.isFinite(decay) || decay <= 0.0 || decay > 1.0) throw new IllegalArgumentException("decay must be in (0,1]");
        if (!Double.isFinite(associationWeight) || associationWeight < 0.0) throw new IllegalArgumentException("associationWeight must be finite and nonnegative");
        if (!Double.isFinite(emptyHistoryActivation)) throw new IllegalArgumentException("emptyHistoryActivation must be finite");
        this.decay = decay;
        this.associationWeight = associationWeight;
        this.emptyHistoryActivation = emptyHistoryActivation;
    }
    @Override public ActivationEstimate estimate(ActivationInput input) {
        Objects.requireNonNull(input, "input");
        double strength = 0.0;
        for (var access : input.accessHistory()) {
            double ageDays = Math.max(0.0, Duration.between(access, input.evaluatedAt()).toMillis() / 86_400_000.0);
            strength += Math.pow(ageDays + 1.0, -decay);
        }
        double base = strength == 0.0 ? emptyHistoryActivation : Math.log(strength);
        double associative = input.queryCues().stream()
                .mapToDouble(cue -> input.cueAssociations().getOrDefault(cue, 0.0)).sum() * associationWeight;
        return new ActivationEstimate(base, associative, base + associative);
    }
}
