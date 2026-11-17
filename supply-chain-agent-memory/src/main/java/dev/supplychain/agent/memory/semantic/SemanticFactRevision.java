package dev.supplychain.agent.memory.semantic;

import java.util.Objects;

/** Proposed fact segment and its expected revision for an atomic temporal amendment. */
public record SemanticFactRevision(SemanticFactId factId, long expectedVersion) {
    public SemanticFactRevision {
        factId = Objects.requireNonNull(factId, "factId");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion cannot be negative");
    }
}
