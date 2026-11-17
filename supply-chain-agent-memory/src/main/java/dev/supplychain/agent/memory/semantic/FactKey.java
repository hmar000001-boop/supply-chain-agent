package dev.supplychain.agent.memory.semantic;

import dev.supplychain.agent.memory.common.EntityReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.util.Objects;

public record FactKey(MemoryScopeId scopeId, EntityReference subject, String predicate) {
    public FactKey {
        scopeId = Objects.requireNonNull(scopeId, "scopeId");
        subject = Objects.requireNonNull(subject, "subject");
        predicate = Objects.requireNonNull(predicate, "predicate").trim();
        if (predicate.isEmpty()) throw new IllegalArgumentException("predicate cannot be blank");
    }
}
