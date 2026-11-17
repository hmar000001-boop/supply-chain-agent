package dev.supplychain.agent.memory.procedural;

import java.util.Objects;
import java.util.UUID;

public record ProcedureVersionId(String value) {
    public ProcedureVersionId {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("value cannot be blank");
    }
    public static ProcedureVersionId random() { return new ProcedureVersionId(UUID.randomUUID().toString()); }
}
