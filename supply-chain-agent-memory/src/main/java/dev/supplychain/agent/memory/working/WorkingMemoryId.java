package dev.supplychain.agent.memory.working;

import java.util.Objects;
import java.util.UUID;

public record WorkingMemoryId(String value) {
    public WorkingMemoryId {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("value cannot be blank");
    }

    public static WorkingMemoryId random() { return new WorkingMemoryId(UUID.randomUUID().toString()); }
}
