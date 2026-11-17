package dev.supplychain.agent.actions.internal;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.working.MemoryValue;
import dev.supplychain.agent.memory.working.WorkingMemoryId;
import java.util.List;
import java.util.Objects;

public record WorkingMemoryUpdateRequest(MemoryScopeId scopeId, WorkingMemoryId memoryId,
                                         long expectedVersion, String key, MemoryValue value,
                                         List<EvidenceReference> evidence) {
    public WorkingMemoryUpdateRequest {
        scopeId = Objects.requireNonNull(scopeId, "scopeId");
        memoryId = Objects.requireNonNull(memoryId, "memoryId");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion cannot be negative");
        key = Objects.requireNonNull(key, "key").trim();
        if (key.isEmpty()) throw new IllegalArgumentException("key cannot be blank");
        value = Objects.requireNonNull(value, "value");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
    }
    public String canonical() { return scopeId.value() + "|" + memoryId.value() + "|" + expectedVersion + "|" + key + "|" + value + "|" + evidence; }
}
