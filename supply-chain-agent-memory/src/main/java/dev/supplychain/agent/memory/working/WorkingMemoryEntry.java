package dev.supplychain.agent.memory.working;

import dev.supplychain.agent.memory.common.EvidenceReference;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** One active-context slot with its own evidence trace, not just a global bag of citations. */
public record WorkingMemoryEntry(MemoryValue value, List<EvidenceReference> evidence, Instant updatedAt) {
    public WorkingMemoryEntry {
        value = Objects.requireNonNull(value, "value");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
