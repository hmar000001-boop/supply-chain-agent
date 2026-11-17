package dev.supplychain.agent.memory.semantic;

import dev.supplychain.agent.memory.common.EntityReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.Objects;

public record SemanticFactQuery(MemoryScopeId scopeId, EntityReference subject,
                                String predicate, Instant validAt, Instant knownAt, int limit) {
    public SemanticFactQuery {
        scopeId = Objects.requireNonNull(scopeId, "scopeId");
        subject = Objects.requireNonNull(subject, "subject");
        predicate = predicate == null || predicate.isBlank() ? null : predicate.trim();
        validAt = Objects.requireNonNull(validAt, "validAt");
        knownAt = Objects.requireNonNull(knownAt, "knownAt");
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be in [1,500]");
    }
}
