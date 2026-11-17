package dev.supplychain.agent.domain.disruption;

import java.util.Objects;

/** Stable, caller-supplied identifier for a disruption domain entity. */
public record DisruptionId(String value) {
    public DisruptionId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("DisruptionId cannot be blank");
        if (value.length() > 128) throw new IllegalArgumentException("DisruptionId exceeds 128 characters");
    }
    @Override public String toString() { return value; }
}
