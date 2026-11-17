package dev.supplychain.agent.mechanisms.b1;

import java.time.Duration;

/** Configurable activation-to-predicted-cognitive-latency approximation; separate from measured I/O latency. */
public final class ExponentialActivationLatencyModel implements CognitiveRetrievalLatencyModel {
    private final double scaleMillis;
    private final double activationDecay;
    private final long minimumMillis;
    private final long maximumMillis;
    public ExponentialActivationLatencyModel(double scaleMillis, double activationDecay, long minimumMillis, long maximumMillis) {
        if (!Double.isFinite(scaleMillis) || scaleMillis <= 0.0) throw new IllegalArgumentException("scaleMillis must be positive");
        if (!Double.isFinite(activationDecay) || activationDecay <= 0.0) throw new IllegalArgumentException("activationDecay must be positive");
        if (minimumMillis < 0 || maximumMillis < Math.max(1, minimumMillis)) throw new IllegalArgumentException("invalid latency bounds");
        this.scaleMillis = scaleMillis;
        this.activationDecay = activationDecay;
        this.minimumMillis = minimumMillis;
        this.maximumMillis = maximumMillis;
    }
    @Override public Duration predict(ActivationEstimate activation) {
        double estimate = scaleMillis * Math.exp(-activationDecay * activation.totalActivation());
        long bounded = Math.max(minimumMillis, Math.min(maximumMillis, Math.round(estimate)));
        return Duration.ofMillis(bounded);
    }
}
