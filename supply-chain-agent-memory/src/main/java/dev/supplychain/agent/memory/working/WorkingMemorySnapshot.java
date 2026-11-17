package dev.supplychain.agent.memory.working;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, versioned active context for one execution scope. */
public record WorkingMemorySnapshot(
        WorkingMemoryId id,
        MemoryScopeId scopeId,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        Map<String, WorkingMemoryEntry> values,
        Map<HypothesisId, Hypothesis> hypotheses,
        List<EvidenceReference> sourceEvidence) {

    public WorkingMemorySnapshot {
        id = Objects.requireNonNull(id, "id");
        scopeId = Objects.requireNonNull(scopeId, "scopeId");
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (updatedAt.isBefore(createdAt) || expiresAt.isBefore(updatedAt)) {
            throw new IllegalArgumentException("invalid working-memory timestamps");
        }
        values = java.util.Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(values, "values")));
        if (values.keySet().stream().anyMatch(key -> key == null || key.isBlank())) {
            throw new IllegalArgumentException("working-memory keys cannot be blank");
        }
        hypotheses = java.util.Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(hypotheses, "hypotheses")));
        sourceEvidence = List.copyOf(Objects.requireNonNull(sourceEvidence, "sourceEvidence"));
        if (hypotheses.entrySet().stream().anyMatch(e -> !e.getKey().equals(e.getValue().id()))) {
            throw new IllegalArgumentException("hypothesis map key does not match hypothesis id");
        }
        if (!sourceEvidence.equals(collectEvidence(values, hypotheses))) {
            throw new IllegalArgumentException("sourceEvidence must equal the evidence referenced by current values and hypotheses");
        }
    }

    public static WorkingMemorySnapshot empty(MemoryScopeId scope, Instant now, Instant expiresAt) {
        return new WorkingMemorySnapshot(WorkingMemoryId.random(), scope, 0, now, now,
                expiresAt, Map.of(), Map.of(), List.of());
    }

    public WorkingMemorySnapshot put(String key, MemoryValue value, List<EvidenceReference> evidence,
                                     Instant now, Instant newExpiry) {
        String slot = requireKey(key);
        var updatedValues = new LinkedHashMap<>(values);
        List<EvidenceReference> slotEvidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        updatedValues.put(slot, new WorkingMemoryEntry(Objects.requireNonNull(value, "value"), slotEvidence, now));
        return revise(updatedValues, hypotheses, now, newExpiry);
    }

    public WorkingMemorySnapshot remove(String key, Instant now, Instant newExpiry) {
        var updatedValues = new LinkedHashMap<>(values);
        updatedValues.remove(requireKey(key));
        return revise(updatedValues, hypotheses, now, newExpiry);
    }

    public WorkingMemorySnapshot putHypothesis(Hypothesis hypothesis, Instant now, Instant newExpiry) {
        var updated = new LinkedHashMap<>(hypotheses);
        updated.put(hypothesis.id(), hypothesis);
        return revise(values, updated, now, newExpiry);
    }

    private WorkingMemorySnapshot revise(Map<String, WorkingMemoryEntry> nextValues,
                                         Map<HypothesisId, Hypothesis> nextHypotheses,
                                         Instant now, Instant newExpiry) {
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("working memory cannot move backwards in time");
        if (!Objects.requireNonNull(newExpiry, "newExpiry").isAfter(now)) {
            throw new IllegalArgumentException("newExpiry must be after update time");
        }
        return new WorkingMemorySnapshot(id, scopeId, version + 1, createdAt, now,
                newExpiry, nextValues, nextHypotheses, collectEvidence(nextValues, nextHypotheses));
    }

    private static List<EvidenceReference> collectEvidence(Map<String, WorkingMemoryEntry> currentValues,
                                                             Map<HypothesisId, Hypothesis> currentHypotheses) {
        var refs = new java.util.LinkedHashSet<EvidenceReference>();
        currentValues.values().forEach(entry -> refs.addAll(entry.evidence()));
        currentHypotheses.values().forEach(hypothesis -> {
            refs.addAll(hypothesis.supportingEvidence());
            refs.addAll(hypothesis.contradictingEvidence());
        });
        return List.copyOf(refs);
    }

    private static String requireKey(String key) {
        String result = Objects.requireNonNull(key, "key").trim();
        if (result.isEmpty()) throw new IllegalArgumentException("working-memory key cannot be blank");
        return result;
    }
}
