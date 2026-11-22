package dev.supplychain.agent.evaluation.experiment.research;

import java.util.Objects;

/** Verifies independently reconstructed safety-counter matrices against an Ed25519 attestation. */
public final class Ed25519M8ExperimentSafetyEvidenceVerifier implements M8ExperimentSafetyEvidenceVerifier {
    private final M8Ed25519AttestationTrust trust;
    private final String issuerId;
    public Ed25519M8ExperimentSafetyEvidenceVerifier(M8Ed25519AttestationTrust trust, String issuerId) {
        this.trust = Objects.requireNonNull(trust, "trust");
        this.issuerId = Objects.requireNonNull(issuerId, "issuerId").trim();
        if (!this.issuerId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) throw new IllegalArgumentException("invalid safety issuer ID");
    }
    @Override public String verifierId() { return issuerId; }
    @Override public void verify(M8ExperimentSafetyEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        if (!issuerId.equals(evidence.verifierId())) throw new SecurityException("safety evidence verifier identity mismatch");
        evidence.requireSafe();
        trust.verify("M8_SAFETY_" + evidence.split().name(), evidence.evidenceFingerprint(), issuerId);
    }
}
