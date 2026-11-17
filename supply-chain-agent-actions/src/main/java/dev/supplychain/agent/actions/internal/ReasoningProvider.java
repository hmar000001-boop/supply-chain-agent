package dev.supplychain.agent.actions.internal;

@FunctionalInterface
public interface ReasoningProvider {
    ReasoningAssessment assess(ReasoningRequest request);
}
