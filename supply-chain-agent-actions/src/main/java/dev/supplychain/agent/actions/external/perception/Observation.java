package dev.supplychain.agent.actions.external.perception;

import dev.supplychain.agent.memory.common.EntityReference;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.Provenance;
import java.util.Objects;

/** A read result tied to a snapshot/version and explicit source provenance. */
public record Observation<T>(T value, EntityReference entity, EvidenceReference evidence,
                             Provenance provenance, long sourceVersion) {
    public Observation {
        value = Objects.requireNonNull(value, "value");
        entity = Objects.requireNonNull(entity, "entity");
        evidence = Objects.requireNonNull(evidence, "evidence");
        provenance = Objects.requireNonNull(provenance, "provenance");
        if (sourceVersion < 0) throw new IllegalArgumentException("sourceVersion cannot be negative");
    }
}
