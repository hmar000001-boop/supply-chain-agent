package dev.supplychain.agent.mechanisms.b1;

@FunctionalInterface
public interface B1CandidateFeatureProvider { B1CandidateFeatures featuresFor(B1MemoryCandidateReference reference); }
