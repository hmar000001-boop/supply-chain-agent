package dev.supplychain.agent.memory.common;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Where a memory item came from and when it was observed and recorded. */
public record Provenance(
        ProvenanceSource source,
        String sourceId,
        Instant observedAt,
        Instant recordedAt,
        String sourceVersion) {

    public Provenance {
        source = Objects.requireNonNull(source, "source");
        sourceId = require(sourceId, "sourceId");
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
        recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        if (recordedAt.isBefore(observedAt)) {
            throw new IllegalArgumentException("recordedAt cannot precede observedAt");
        }
        sourceVersion = sourceVersion == null || sourceVersion.isBlank() ? null : sourceVersion.trim();
    }

    public Optional<String> sourceVersionOptional() {
        return Optional.ofNullable(sourceVersion);
    }

    public boolean isModelDerived() {
        return source == ProvenanceSource.MODEL_DERIVED;
    }

    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
