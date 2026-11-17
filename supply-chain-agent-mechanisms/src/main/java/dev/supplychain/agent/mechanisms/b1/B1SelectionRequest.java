package dev.supplychain.agent.mechanisms.b1;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record B1SelectionRequest(MemoryScopeId scopeId, Set<String> queryCues, Instant evaluatedAt,
                                 int contextBudgetUnits, int maximumCandidates,
                                 Duration maintenanceHorizon, B1ScoringPolicy policy) {
    public B1SelectionRequest {
        scopeId = Objects.requireNonNull(scopeId, "scopeId");
        queryCues = Set.copyOf(Objects.requireNonNull(queryCues, "queryCues"));
        if (queryCues.stream().anyMatch(s -> s == null || s.isBlank())) throw new IllegalArgumentException("query cues cannot be blank");
        evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        if (contextBudgetUnits < 1 || maximumCandidates < 1) throw new IllegalArgumentException("selection budgets must be positive");
        maintenanceHorizon = Objects.requireNonNull(maintenanceHorizon, "maintenanceHorizon");
        if (maintenanceHorizon.isNegative()) throw new IllegalArgumentException("maintenance horizon cannot be negative");
        policy = Objects.requireNonNull(policy, "policy");
    }
}
