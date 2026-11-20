package dev.supplychain.agent.mechanisms.b3;

import java.util.Objects;

/**
 * Capability-like wrapper for an outcome loaded from the repository after it passed the B3
 * verifier. Its constructor is package-private so other packages cannot label arbitrary records
 * as verified; the trusted B3 service is the only production minting boundary.
 */
public final class VerifiedDownstreamOutcomeEvidence {
    private final DownstreamOutcomeEvidence evidence;

    VerifiedDownstreamOutcomeEvidence(DownstreamOutcomeEvidence evidence) {
        this.evidence = Objects.requireNonNull(evidence, "evidence");
    }

    public DownstreamOutcomeEvidence evidence() { return evidence; }
}
