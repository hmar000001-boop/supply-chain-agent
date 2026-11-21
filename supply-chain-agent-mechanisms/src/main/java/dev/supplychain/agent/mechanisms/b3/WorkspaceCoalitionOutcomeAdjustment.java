package dev.supplychain.agent.mechanisms.b3;

import dev.supplychain.agent.memory.common.MemoryScopeId;

/** Activated, scope-bound score overlay learned from verified outcomes of exact coalition signatures. */
@FunctionalInterface
public interface WorkspaceCoalitionOutcomeAdjustment {
    double adjustmentFor(MemoryScopeId scopeId, String coalitionPreferenceKey);
    static WorkspaceCoalitionOutcomeAdjustment neutral() { return (scope,key) -> 0.0; }
}
