package dev.supplychain.agent.mechanisms.b3;

import java.time.Instant;

/** Trusted host boundary that reopens independent baseline/candidate holdout run records. */
@FunctionalInterface
public interface B3CoalitionFeedbackEvidenceVerifier {
    boolean verify(B3CoalitionFeedbackCandidate candidate,B3CoalitionFeedbackObservation observation,Instant evaluatedAt);
}
