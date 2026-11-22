package dev.supplychain.agent.evaluation.experiment.research;

import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

/** Fail-closed Ed25519 trust service over separately provisioned public keys and signed records. */
public final class M8Ed25519AttestationTrust {
    private final M8SignedAttestationRepository repository;
    private final Map<String, PublicKey> keys;
    private final Clock clock;
    private final Duration maximumAge;

    public M8Ed25519AttestationTrust(M8SignedAttestationRepository repository,
            Map<String, PublicKey> keys, Clock clock, Duration maximumAge) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.keys = Map.copyOf(Objects.requireNonNull(keys, "keys"));
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maximumAge = Objects.requireNonNull(maximumAge, "maximumAge");
        if (this.keys.isEmpty() || maximumAge.isZero() || maximumAge.isNegative())
            throw new IllegalArgumentException("trust requires pinned issuer keys and a positive maximum age");
        var fingerprints = new HashSet<String>();
        this.keys.forEach((issuer, key) -> {
            if (issuer == null || !issuer.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}") || key == null
                    || (!"EdDSA".equalsIgnoreCase(key.getAlgorithm()) && !"Ed25519".equalsIgnoreCase(key.getAlgorithm())))
                throw new IllegalArgumentException("trust map contains an invalid issuer or non-Ed25519 key");
            String fingerprint = M8CandidateCodeFingerprint.sha256(key.getEncoded());
            if (!fingerprints.add(fingerprint)) throw new SecurityException("distinct trusted issuers cannot share key material");
        });
    }

    public void verify(String evidenceType, String subjectFingerprint, String issuerId) {
        Objects.requireNonNull(evidenceType, "evidenceType");
        Objects.requireNonNull(subjectFingerprint, "subjectFingerprint");
        Objects.requireNonNull(issuerId, "issuerId");
        PublicKey key = keys.get(issuerId);
        if (key == null) throw new SecurityException("attestation issuer is not pinned: " + issuerId);
        M8SignedAttestation attestation;
        try {
            attestation = repository.find(evidenceType, subjectFingerprint)
                    .orElseThrow(() -> new SecurityException("required signed attestation is absent: " + evidenceType));
        } catch (java.io.IOException failure) {
            throw new SecurityException("cannot read signed attestation", failure);
        }
        if (!attestation.evidenceType().equals(evidenceType)
                || !attestation.subjectFingerprint().equals(subjectFingerprint)
                || !attestation.issuerId().equals(issuerId))
            throw new SecurityException("signed attestation identity does not match the requested evidence");
        Instant now = clock.instant();
        if (attestation.issuedAt().isAfter(now.plusSeconds(30))
                || attestation.issuedAt().isBefore(now.minus(maximumAge)))
            throw new SecurityException("signed attestation is expired or issued in the future");
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(attestation.signedPayload());
            if (!verifier.verify(java.util.Base64.getDecoder().decode(attestation.signatureBase64())))
                throw new SecurityException("Ed25519 signature verification failed");
        } catch (SecurityException invalid) {
            throw invalid;
        } catch (Exception failure) {
            throw new SecurityException("Ed25519 attestation verification failed", failure);
        }
    }

    public boolean verifyQuietly(String evidenceType, String subjectFingerprint, String issuerId) {
        try { verify(evidenceType, subjectFingerprint, issuerId); return true; }
        catch (RuntimeException invalid) { return false; }
    }
}
