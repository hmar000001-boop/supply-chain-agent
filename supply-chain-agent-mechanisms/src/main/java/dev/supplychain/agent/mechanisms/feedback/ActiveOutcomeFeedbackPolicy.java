package dev.supplychain.agent.mechanisms.feedback;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Immutable scope-bound overlay minted only after held-out evidence and host authorization. */
public final class ActiveOutcomeFeedbackPolicy implements CandidateOutcomeAdjustment {
    private final MemoryScopeId scopeId;private final String policyId;private final long version;
    private final String evaluationFingerprint;private final String approverId;private final Instant activatedAt;
    private final Map<String,Double> adjustments;
    ActiveOutcomeFeedbackPolicy(OutcomeFeedbackPolicyCandidate candidate,FeedbackPolicyEvaluationReport evaluation,
            FeedbackPolicyActivationApproval approval) {
        this.scopeId=candidate.scopeId();this.policyId=candidate.policyId();this.version=candidate.version();
        this.evaluationFingerprint=evaluation.evaluationFingerprint();this.approverId=approval.approverId();
        this.activatedAt=approval.approvedAt();this.adjustments=candidate.candidateAdjustments();
    }
    @Override public double adjustmentFor(MemoryScopeId requestedScope,String candidateId) {
        Objects.requireNonNull(requestedScope,"scopeId");
        return scopeId.equals(requestedScope)?adjustments.getOrDefault(candidateId,0.0):0.0;
    }
    public MemoryScopeId scopeId(){return scopeId;}public String policyId(){return policyId;}public long version(){return version;}
    public String evaluationFingerprint(){return evaluationFingerprint;}public String approverId(){return approverId;}
    public Instant activatedAt(){return activatedAt;}
}
