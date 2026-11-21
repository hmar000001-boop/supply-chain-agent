package dev.supplychain.agent.mechanisms.b3;

import java.time.Instant;

@FunctionalInterface
public interface B3CoalitionFeedbackActivationAuthority {
    boolean isAuthorized(B3CoalitionFeedbackCandidate candidate,B3CoalitionFeedbackEvaluationReport report,
                         String approverId,String approvalReference,Instant now);
}
