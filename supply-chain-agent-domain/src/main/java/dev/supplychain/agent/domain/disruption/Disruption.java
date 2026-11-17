package dev.supplychain.agent.domain.disruption;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Recorded disruption observation; source/provenance is added by the M2/M3 evidence layer. */
public record Disruption(
        DisruptionId id,
        DisruptionType type,
        DisruptionSeverity severity,
        DisruptionStatus status,
        String description,
        Instant occurredAt,
        Set<String> affectedEntityReferences) {
    public Disruption {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(description, "description");
        description = description.trim();
        if (description.isEmpty()) throw new IllegalArgumentException("description cannot be blank");
        Objects.requireNonNull(occurredAt, "occurredAt");
        affectedEntityReferences = Set.copyOf(Objects.requireNonNull(affectedEntityReferences, "affectedEntityReferences"));
    }
}
