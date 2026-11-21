package dev.supplychain.agent.mechanisms.b3;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.Map;

/** Immutable overlay created only after independent holdout validation and host authorization. */
public final class ActiveB3CoalitionFeedbackPolicy implements WorkspaceCoalitionOutcomeAdjustment {
    private final MemoryScopeId scopeId;private final String policyId;private final long version;
    private final String evaluationFingerprint;private final String approverId;private final Instant activatedAt;
    private final Map<String,Double> adjustments;
    ActiveB3CoalitionFeedbackPolicy(B3CoalitionFeedbackCandidate candidate,B3CoalitionFeedbackEvaluationReport report,
            B3CoalitionFeedbackActivationApproval approval) {
        scopeId=candidate.scopeId();policyId=candidate.policyId();version=candidate.version();
        evaluationFingerprint=report.evaluationFingerprint();approverId=approval.approverId();activatedAt=approval.approvedAt();
        adjustments=candidate.preferenceAdjustments();
    }
    @Override public double adjustmentFor(MemoryScopeId scope,String preferenceKey){return scopeId.equals(scope)?adjustments.getOrDefault(preferenceKey,0.0):0.0;}
    public MemoryScopeId scopeId(){return scopeId;}public String policyId(){return policyId;}public long version(){return version;}
    public String evaluationFingerprint(){return evaluationFingerprint;}public String approverId(){return approverId;}public Instant activatedAt(){return activatedAt;}
}
