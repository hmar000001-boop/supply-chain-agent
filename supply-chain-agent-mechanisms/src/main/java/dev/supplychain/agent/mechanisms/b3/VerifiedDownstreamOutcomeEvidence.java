package dev.supplychain.agent.mechanisms.b3;

import java.util.Objects;

/**
 * Capability-like wrapper for an outcome loaded after exact receipt, scope, verifier, evidence,
 * and experimental-unit provenance revalidation. Only the B3 service can mint it.
 */
public final class VerifiedDownstreamOutcomeEvidence {
    private final DownstreamOutcomeEvidence evidence;
    private final String experimentUnitFingerprint;

    VerifiedDownstreamOutcomeEvidence(DownstreamOutcomeEvidence evidence, String experimentUnitFingerprint) {
        this.evidence = Objects.requireNonNull(evidence, "evidence");
        this.experimentUnitFingerprint = Objects.requireNonNull(experimentUnitFingerprint, "experimentUnitFingerprint");
        if (!experimentUnitFingerprint.matches("[0-9a-f]{64}")
                || !experimentUnitFingerprint.equals(evidence.experimentUnitFingerprint())) {
            throw new SecurityException("verified outcome must carry the exact evidence-bound experimental-unit fingerprint");
        }
    }

    public DownstreamOutcomeEvidence evidence() { return evidence; }
    public String experimentUnitFingerprint() { return experimentUnitFingerprint; }
}
