package dev.supplychain.agent.actions.internal;

import dev.supplychain.agent.memory.common.EvidenceReference;
import java.util.List;
import java.util.Objects;

/** Bounded assessment output; it stores concise findings, not private chain-of-thought traces. */
public record ReasoningAssessment(String summary, List<String> evidenceBackedFindings,
                                  List<String> hypotheses, List<String> assumptions,
                                  List<EvidenceReference> supportingEvidence,
                                  List<String> informationGaps, ReasoningConfidence confidence,
                                  String conciseRationale) {
    public ReasoningAssessment {
        summary = require(summary, "summary");
        evidenceBackedFindings = clean(evidenceBackedFindings, "evidenceBackedFindings");
        hypotheses = clean(hypotheses, "hypotheses");
        assumptions = clean(assumptions, "assumptions");
        supportingEvidence = List.copyOf(Objects.requireNonNull(supportingEvidence, "supportingEvidence"));
        informationGaps = clean(informationGaps, "informationGaps");
        confidence = Objects.requireNonNull(confidence, "confidence");
        conciseRationale = require(conciseRationale, "conciseRationale");
        requireMaxLength(summary, 4_000, "summary");
        requireMaxLength(conciseRationale, 2_000, "conciseRationale");
        requireMaxItems(evidenceBackedFindings, 50, "evidenceBackedFindings");
        requireMaxItems(hypotheses, 50, "hypotheses");
        requireMaxItems(assumptions, 50, "assumptions");
        requireMaxItems(informationGaps, 50, "informationGaps");
        requireItemLengths(evidenceBackedFindings, 1_000, "evidenceBackedFindings");
        requireItemLengths(hypotheses, 1_000, "hypotheses");
        requireItemLengths(assumptions, 1_000, "assumptions");
        requireItemLengths(informationGaps, 1_000, "informationGaps");
        requireMaxItems(supportingEvidence, 200, "supportingEvidence");
        if (!evidenceBackedFindings.isEmpty() && supportingEvidence.isEmpty()) {
            throw new IllegalArgumentException("evidence-backed findings require supporting evidence");
        }
    }
    private static List<String> clean(List<String> values, String name) {
        return Objects.requireNonNull(values, name).stream().map(value -> require(value, name + " item")).distinct().toList();
    }
    private static void requireMaxLength(String value, int maximum, String name) {
        if (value.length() > maximum) throw new IllegalArgumentException(name + " exceeds " + maximum + " characters");
    }
    private static void requireMaxItems(List<?> values, int maximum, String name) {
        if (values.size() > maximum) throw new IllegalArgumentException(name + " exceeds " + maximum + " items");
    }
    private static void requireItemLengths(List<String> values, int maximum, String name) {
        if (values.stream().anyMatch(value -> value.length() > maximum)) {
            throw new IllegalArgumentException(name + " contains an item longer than " + maximum + " characters");
        }
    }
    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
