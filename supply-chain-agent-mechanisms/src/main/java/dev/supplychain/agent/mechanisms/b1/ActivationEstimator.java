package dev.supplychain.agent.mechanisms.b1;

@FunctionalInterface
public interface ActivationEstimator { ActivationEstimate estimate(ActivationInput input); }
