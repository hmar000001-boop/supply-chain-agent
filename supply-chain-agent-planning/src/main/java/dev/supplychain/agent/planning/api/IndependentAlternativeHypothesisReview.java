package dev.supplychain.agent.planning.api;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.mechanisms.b4.ObjectiveFingerprint;
import dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Host-issued evidence that alternative hypotheses were independently reviewed for one exact
 * execution, objective, uncertainty revision and pre-review planning input. It is evidence only;
 * it never authorizes an external action.
 */
public record IndependentAlternativeHypothesisReview(
        String executionId,
        UncertaintyAssessment.Reference assessmentReference,
        String objectiveFingerprint,
        String basisPlanningFingerprint,
        List<String> alternativeHypothesisKeys,
        String reviewerId,
        EvidenceReference evidenceReference,
        Instant reviewedAt,
        String reviewFingerprint) {

    public IndependentAlternativeHypothesisReview {
        executionId = required(executionId, "executionId");
        assessmentReference = Objects.requireNonNull(assessmentReference, "assessmentReference");
        objectiveFingerprint = sha(objectiveFingerprint, "objectiveFingerprint");
        basisPlanningFingerprint = sha(basisPlanningFingerprint, "basisPlanningFingerprint");
        alternativeHypothesisKeys = List.copyOf(Objects.requireNonNull(alternativeHypothesisKeys, "alternativeHypothesisKeys"));
        if (alternativeHypothesisKeys.size() < 2 || alternativeHypothesisKeys.stream().anyMatch(v -> !key(v))
                || alternativeHypothesisKeys.stream().distinct().count() != alternativeHypothesisKeys.size()) {
            throw new IllegalArgumentException("independent review requires at least two unique registered alternative-hypothesis keys");
        }
        reviewerId = required(reviewerId, "reviewerId");
        evidenceReference = Objects.requireNonNull(evidenceReference, "evidenceReference");
        reviewedAt = Objects.requireNonNull(reviewedAt, "reviewedAt");
        reviewFingerprint = sha(reviewFingerprint, "reviewFingerprint");
        if (!reviewFingerprint.equals(fingerprint(executionId, assessmentReference, objectiveFingerprint,
                basisPlanningFingerprint, alternativeHypothesisKeys, reviewerId, evidenceReference, reviewedAt))) {
            throw new SecurityException("alternative-hypothesis review fingerprint does not match its complete evidence binding");
        }
    }

    public static IndependentAlternativeHypothesisReview create(String executionId,
            UncertaintyAssessment.Reference assessmentReference, String objectiveFingerprint,
            String basisPlanningFingerprint, List<String> alternativeHypothesisKeys, String reviewerId,
            EvidenceReference evidenceReference, Instant reviewedAt) {
        return new IndependentAlternativeHypothesisReview(executionId, assessmentReference, objectiveFingerprint,
                basisPlanningFingerprint, alternativeHypothesisKeys, reviewerId, evidenceReference, reviewedAt,
                fingerprint(executionId, assessmentReference, objectiveFingerprint, basisPlanningFingerprint,
                        alternativeHypothesisKeys, reviewerId, evidenceReference, reviewedAt));
    }

    private static String fingerprint(String executionId, UncertaintyAssessment.Reference reference,
            String objectiveFingerprint, String basisPlanningFingerprint, List<String> alternatives,
            String reviewer, EvidenceReference evidence, Instant at) {
        String canonical = required(executionId, "executionId") + "|" + Objects.requireNonNull(reference, "reference")
                + "|" + objectiveFingerprint + "|" + basisPlanningFingerprint + "|"
                + List.copyOf(alternatives) + "|" + reviewer + "|" + evidence.evidenceType() + ":"
                + evidence.evidenceId() + "|" + at;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static boolean key(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,159}");
    }
    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
    private static String sha(String value, String name) {
        String result = required(value, name);
        if (!result.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        return result;
    }
}
