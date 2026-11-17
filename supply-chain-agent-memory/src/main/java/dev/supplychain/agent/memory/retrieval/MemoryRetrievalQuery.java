package dev.supplychain.agent.memory.retrieval;

import dev.supplychain.agent.memory.common.EntityReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.working.WorkingMemoryId;
import java.time.Instant;
import java.util.Objects;

/** Exact, deterministic lookup request. Ranking/activation heuristics belong to B1, not this layer. */
public record MemoryRetrievalQuery(MemoryScopeId scopeId, EntityReference entity,
                                   String semanticPredicate, WorkingMemoryId workingMemoryId,
                                   Instant validAt, Instant knownAt, int limitPerType) {
    public MemoryRetrievalQuery {
        scopeId = Objects.requireNonNull(scopeId, "scopeId");
        semanticPredicate = semanticPredicate == null || semanticPredicate.isBlank() ? null : semanticPredicate.trim();
        validAt = Objects.requireNonNull(validAt, "validAt");
        knownAt = Objects.requireNonNull(knownAt, "knownAt");
        if (limitPerType < 1 || limitPerType > 500) throw new IllegalArgumentException("limitPerType must be in [1,500]");
    }
}
