package dev.supplychain.agent.memory.semantic;

import java.util.Objects;
import java.util.UUID;

public record SemanticFactId(String value) {
    public SemanticFactId {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("value cannot be blank");
    }
    public static SemanticFactId random() { return new SemanticFactId(UUID.randomUUID().toString()); }
}
