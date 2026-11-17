package dev.supplychain.agent.memory.common;

import java.util.Objects;

/** Isolation boundary for memory owned by one tenant, business unit or execution family. */
public record MemoryScopeId(String value) {
    public MemoryScopeId {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("scope value cannot be blank");
    }
}
