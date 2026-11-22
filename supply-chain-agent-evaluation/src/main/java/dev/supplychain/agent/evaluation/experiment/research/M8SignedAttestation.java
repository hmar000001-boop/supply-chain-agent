package dev.supplychain.agent.evaluation.experiment.research;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/** Immutable Ed25519 signature over an exact evidence type, subject fingerprint, issuer and timestamp. */
public record M8SignedAttestation(String evidenceType, String subjectFingerprint, String issuerId,
        Instant issuedAt, String signatureBase64) {
    public M8SignedAttestation {
        evidenceType = token(evidenceType, "evidenceType", "[A-Z][A-Z0-9_]{1,95}");
        subjectFingerprint = sha(subjectFingerprint, "subjectFingerprint");
        issuerId = token(issuerId, "issuerId", "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
        issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        signatureBase64 = Objects.requireNonNull(signatureBase64, "signatureBase64").trim();
        byte[] signature;
        try { signature = Base64.getDecoder().decode(signatureBase64); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("signatureBase64 is not valid Base64", invalid); }
        if (signature.length != 64) throw new IllegalArgumentException("Ed25519 signatures must contain exactly 64 bytes");
    }

    /** Canonical versioned payload used by both the external signer and verifier. */
    public byte[] signedPayload() {
        String canonical = "M8-ED25519-ATTESTATION-V1\n" + evidenceType + "\n" + subjectFingerprint
                + "\n" + issuerId + "\n" + issuedAt.toString() + "\n";
        return canonical.getBytes(StandardCharsets.UTF_8);
    }

    private static String sha(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (!result.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        return result;
    }
    private static String token(String value, String name, String pattern) {
        String result = Objects.requireNonNull(value, name).trim();
        if (!result.matches(pattern)) throw new IllegalArgumentException(name + " is not a safe identity token");
        return result;
    }
}
