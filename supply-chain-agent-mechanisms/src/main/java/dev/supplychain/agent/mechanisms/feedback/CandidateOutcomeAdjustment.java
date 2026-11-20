package dev.supplychain.agent.mechanisms.feedback;

import dev.supplychain.agent.memory.common.MemoryScopeId;

/** Bounded additive overlay, explicitly keyed by tenant scope and candidate identity. */
@FunctionalInterface
public interface CandidateOutcomeAdjustment {
    double adjustmentFor(MemoryScopeId scopeId, String candidateId);
    static CandidateOutcomeAdjustment neutral() { return (scopeId, candidateId) -> 0.0; }
}
