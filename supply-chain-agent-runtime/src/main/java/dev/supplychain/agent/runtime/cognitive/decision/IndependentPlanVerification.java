package dev.supplychain.agent.runtime.cognitive.decision;

import dev.supplychain.agent.planning.api.PlanCandidate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/** Host-issued proof that a separate verifier checked the exact selected plan and request. */
public record IndependentPlanVerification(String executionId, String candidateId,
        String objectiveFingerprint, String planningRequestFingerprint, String candidateFingerprint,
        String verifierId, String evidenceReference, Instant verifiedAt) {
    public IndependentPlanVerification {
        executionId = required(executionId, "executionId");
        candidateId = required(candidateId, "candidateId");
        objectiveFingerprint = required(objectiveFingerprint, "objectiveFingerprint");
        planningRequestFingerprint = sha256(planningRequestFingerprint, "planningRequestFingerprint");
        candidateFingerprint = sha256(candidateFingerprint, "candidateFingerprint");
        verifierId = required(verifierId, "verifierId");
        evidenceReference = required(evidenceReference, "evidenceReference");
        verifiedAt = Objects.requireNonNull(verifiedAt, "verifiedAt");
    }

    public static String fingerprint(PlanCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        String canonical = candidate.candidateId() + "|" + candidate.objectiveId().value() + "|"
                + candidate.actions().stream().map(Object::toString).collect(java.util.stream.Collectors.joining("\n"));
        return digest(canonical);
    }

    private static String sha256(String value, String name) {
        String result = required(value, name);
        if (!result.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(name + " must be SHA-256");
        return result;
    }
    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
}
