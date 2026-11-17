package dev.supplychain.agent.memory.episodic;

import dev.supplychain.agent.memory.common.EntityReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.util.Objects;

public record EpisodeQuery(MemoryScopeId scopeId, EntityReference entity, java.time.Instant knownAt, int limit) {
    public EpisodeQuery {
        scopeId = Objects.requireNonNull(scopeId, "scopeId");
        knownAt = Objects.requireNonNull(knownAt, "knownAt");
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be in [1,500]");
    }
}
