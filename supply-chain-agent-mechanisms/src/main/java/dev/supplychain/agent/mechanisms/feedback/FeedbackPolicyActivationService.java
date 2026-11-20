package dev.supplychain.agent.mechanisms.feedback;

import java.time.Instant;
import java.util.Objects;

/** Separate trusted activation boundary; passing evaluation never activates a candidate by itself. */
public final class FeedbackPolicyActivationService {
    private final FeedbackPolicyActivationAuthority authority;
    public FeedbackPolicyActivationService(FeedbackPolicyActivationAuthority authority){this.authority=Objects.requireNonNull(authority,"authority");}
    public ActiveOutcomeFeedbackPolicy activate(OutcomeFeedbackPolicyCandidate candidate,FeedbackPolicyEvaluationReport report,
            FeedbackPolicyActivationApproval approval,Instant now) {
        Objects.requireNonNull(candidate,"candidate");Objects.requireNonNull(report,"report");
        Objects.requireNonNull(approval,"approval");Objects.requireNonNull(now,"now");
        if(!candidate.policyId().equals(report.policyId())||candidate.version()!=report.policyVersion())
            throw new SecurityException("evaluation report does not refer to the exact candidate policy version");
        if(!candidate.scopeId().equals(approval.scopeId())||!candidate.policyId().equals(approval.policyId())
                ||candidate.version()!=approval.policyVersion()
                ||!report.evaluationFingerprint().equals(approval.evaluationFingerprint()))
            throw new SecurityException("activation approval is not bound to this exact scope, policy version and evaluation report");
        if(report.decision()!=PolicyDisposition.ELIGIBLE_FOR_AUTHORIZED_ACTIVATION)
            throw new SecurityException("candidate did not pass preregistered held-out evaluation");
        if(approval.approvedAt().isBefore(report.evaluatedAt())||approval.approvedAt().isAfter(now))
            throw new IllegalArgumentException("approval timestamp must be after evaluation and no later than activation");
        if(!authority.isAuthorized(candidate,report,approval.approverId(),approval.approvalReference(),now))
            throw new SecurityException("policy activation approval is not authorized");
        return new ActiveOutcomeFeedbackPolicy(candidate,report,approval);
    }
}
