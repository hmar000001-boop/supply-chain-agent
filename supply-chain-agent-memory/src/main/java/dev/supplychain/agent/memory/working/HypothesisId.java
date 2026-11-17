package dev.supplychain.agent.memory.working;

import java.util.Objects;
import java.util.UUID;

public record HypothesisId(String value) {
    public HypothesisId {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("value cannot be blank");
    }

    public static HypothesisId random() { return new HypothesisId(UUID.randomUUID().toString()); }
}
