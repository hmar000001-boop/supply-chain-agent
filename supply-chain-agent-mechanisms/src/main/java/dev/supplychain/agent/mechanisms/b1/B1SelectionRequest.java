package dev.supplychain.agent.mechanisms.b1;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import dev.supplychain.agent.mechanisms.b6.UncertaintyGovernanceDirective;

public record B1SelectionRequest(MemoryScopeId scopeId, Set<String> queryCues, Instant evaluatedAt,
                                 int contextBudgetUnits, int maximumCandidates,
                                 Duration maintenanceHorizon, B1ScoringPolicy policy,
                                 UncertaintyGovernanceDirective uncertaintyDirective) {
    public B1SelectionRequest {
        scopeId = Objects.requireNonNull(scopeId, "scopeId");
        queryCues = Set.copyOf(Objects.requireNonNull(queryCues, "queryCues"));
        if (queryCues.stream().anyMatch(s -> s == null || s.isBlank())) throw new IllegalArgumentException("query cues cannot be blank");
        evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        if (contextBudgetUnits < 1 || maximumCandidates < 1) throw new IllegalArgumentException("selection budgets must be positive");
        maintenanceHorizon = Objects.requireNonNull(maintenanceHorizon, "maintenanceHorizon");
        if (maintenanceHorizon.isNegative()) throw new IllegalArgumentException("maintenance horizon cannot be negative");
        policy = Objects.requireNonNull(policy, "policy");
        uncertaintyDirective = uncertaintyDirective == null ? neutralDirective(scopeId) : uncertaintyDirective;
        if (!uncertaintyDirective.assessmentReference().memoryScopeId().equals(scopeId.value()))
            throw new SecurityException("B1 uncertainty directive belongs to another scope");
    }
    public B1SelectionRequest(MemoryScopeId scopeId, Set<String> queryCues, Instant evaluatedAt,
            int contextBudgetUnits, int maximumCandidates, Duration maintenanceHorizon, B1ScoringPolicy policy) {
        this(scopeId, queryCues, evaluatedAt, contextBudgetUnits, maximumCandidates, maintenanceHorizon, policy,
                neutralDirective(scopeId));
    }

    public B1SelectionRequest withUncertaintyDirective(UncertaintyGovernanceDirective directive) {
        return new B1SelectionRequest(scopeId, queryCues, evaluatedAt, contextBudgetUnits, maximumCandidates,
                maintenanceHorizon, policy, directive);
    }

    private static UncertaintyGovernanceDirective neutralDirective(MemoryScopeId scope) {
        var reference = new dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment.Reference(
                "b1-standalone-neutral", 1, "b1-standalone", scope.value());
        return new UncertaintyGovernanceDirective(reference,
                dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment.CalibrationStatus.NOT_CALIBRATED,
                "", "", 0, 0, 0, 0, 1, 1, false, false);
    }
}

