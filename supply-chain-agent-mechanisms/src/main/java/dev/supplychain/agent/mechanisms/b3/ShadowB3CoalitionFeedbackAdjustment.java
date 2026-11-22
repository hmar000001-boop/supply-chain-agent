package dev.supplychain.agent.mechanisms.b3;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.util.Objects;

/**
 * Isolated-experiment-only view of a SHADOW B3 candidate. It deliberately does not implement the
 * activation lifecycle and must only be supplied to a disposable evaluation graph.
 */
public final class ShadowB3CoalitionFeedbackAdjustment implements WorkspaceCoalitionOutcomeAdjustment {
    private final B3CoalitionFeedbackCandidate candidate;

    public ShadowB3CoalitionFeedbackAdjustment(B3CoalitionFeedbackCandidate candidate) {
        this.candidate = Objects.requireNonNull(candidate, "candidate");
        if (candidate.disposition() != B3CoalitionFeedbackCandidate.Disposition.SHADOW)
            throw new SecurityException("experiment overlay requires a SHADOW B3 candidate");
    }

    @Override public double adjustmentFor(MemoryScopeId scope, String preferenceKey) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(preferenceKey, "preferenceKey");
        return candidate.scopeId().equals(scope)
                ? candidate.preferenceAdjustments().getOrDefault(preferenceKey, 0.0) : 0.0;
    }

    public B3CoalitionFeedbackCandidate candidate() { return candidate; }
    public String candidateFingerprint() { return B3CoalitionFeedbackPolicyService.candidateFingerprint(candidate); }
}
