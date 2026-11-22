package dev.supplychain.agent.evaluation.experiment.research;

import java.util.Objects;

/** Verifies endpoint evidence against an externally signed, split-specific Ed25519 attestation. */
public final class Ed25519M8VerifiedEndpointEvidenceVerifier implements M8VerifiedEndpointEvidenceVerifier {
    private final M8Ed25519AttestationTrust trust;
    private final String issuerId;
    public Ed25519M8VerifiedEndpointEvidenceVerifier(M8Ed25519AttestationTrust trust, String issuerId) {
        this.trust = Objects.requireNonNull(trust, "trust");
        this.issuerId = require(issuerId);
    }
    @Override public String verifierId() { return issuerId; }
    @Override public void verify(M8VerifiedEndpointEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        if (!issuerId.equals(evidence.verifierId())) throw new SecurityException("endpoint evidence verifier identity mismatch");
        trust.verify("M8_ENDPOINT_" + evidence.split().name(), evidence.evidenceFingerprint(), issuerId);
    }
    private static String require(String value) {
        String result = Objects.requireNonNull(value, "issuerId").trim();
        if (!result.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) throw new IllegalArgumentException("invalid endpoint issuer ID");
        return result;
    }
}
