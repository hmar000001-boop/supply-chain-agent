package dev.supplychain.agent.mechanisms.feedback;

import java.time.Instant;

/**
 * Host authorization receives the exact candidate and full evaluation report so it can
 * independently validate the evidence, protocol and approver scope before activation.
 */
@FunctionalInterface
public interface FeedbackPolicyActivationAuthority {
    boolean isAuthorized(OutcomeFeedbackPolicyCandidate candidate, FeedbackPolicyEvaluationReport report,
                         String approverId, String approvalReference, Instant at);
}
