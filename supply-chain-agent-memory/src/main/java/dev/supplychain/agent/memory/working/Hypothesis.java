package dev.supplychain.agent.memory.working;

import dev.supplychain.agent.memory.common.EvidenceReference;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** A provisional interpretation; it is not promoted to semantic truth by being stored here. */
public record Hypothesis(
        HypothesisId id,
        String statement,
        HypothesisStatus status,
        double confidence,
        List<EvidenceReference> supportingEvidence,
        List<EvidenceReference> contradictingEvidence,
        Instant createdAt,
        Instant updatedAt) {

    public Hypothesis {
        id = Objects.requireNonNull(id, "id");
        statement = require(statement, "statement");
        status = Objects.requireNonNull(status, "status");
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be finite and in [0,1]");
        }
        supportingEvidence = List.copyOf(Objects.requireNonNull(supportingEvidence, "supportingEvidence"));
        contradictingEvidence = List.copyOf(Objects.requireNonNull(contradictingEvidence, "contradictingEvidence"));
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt precedes createdAt");
        if (status == HypothesisStatus.SUPPORTED && supportingEvidence.isEmpty()) {
            throw new IllegalArgumentException("a supported hypothesis requires supporting evidence");
        }
        if (status == HypothesisStatus.REFUTED && contradictingEvidence.isEmpty()) {
            throw new IllegalArgumentException("a refuted hypothesis requires contradicting evidence");
        }
    }

    public static Hypothesis proposed(String statement, double confidence, Instant at) {
        return new Hypothesis(HypothesisId.random(), statement, HypothesisStatus.PROPOSED,
                confidence, List.of(), List.of(), at, at);
    }

    public Hypothesis support(List<EvidenceReference> evidence, Instant at) {
        return new Hypothesis(id, statement, HypothesisStatus.SUPPORTED, confidence,
                evidence, contradictingEvidence, createdAt, at);
    }

    public Hypothesis refute(List<EvidenceReference> evidence, Instant at) {
        return new Hypothesis(id, statement, HypothesisStatus.REFUTED, confidence,
                supportingEvidence, evidence, createdAt, at);
    }

    public Hypothesis withdraw(Instant at) {
        return new Hypothesis(id, statement, HypothesisStatus.WITHDRAWN, confidence,
                supportingEvidence, contradictingEvidence, createdAt, at);
    }

    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
