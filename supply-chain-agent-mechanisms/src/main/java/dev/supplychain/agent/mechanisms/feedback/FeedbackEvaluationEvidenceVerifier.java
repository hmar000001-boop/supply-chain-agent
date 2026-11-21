package dev.supplychain.agent.mechanisms.feedback;

import java.time.Instant;

/**
 * Trusted host boundary for paired holdout runs. Implementations must dereference both run
 * fingerprints from an authoritative evaluation store and verify that both runs were produced
 * from the exact same immutable {@code experimentUnitFingerprint}. The baseline run must use
 * {@code candidate.baselinePolicyVersion()}; the candidate run must use the exact
 * {@code candidate.policyId()} and {@code candidate.version()}. Both records must predate the
 * evaluation timestamp. Merely checking that supplied strings are SHA-256 values is not
 * verification.
 */
@FunctionalInterface
public interface FeedbackEvaluationEvidenceVerifier {
    boolean verify(OutcomeFeedbackPolicyCandidate candidate,
                   FeedbackEvaluationObservation observation, Instant evaluatedAt);
}
