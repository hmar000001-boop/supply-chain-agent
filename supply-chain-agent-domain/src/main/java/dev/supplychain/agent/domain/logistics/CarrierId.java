package dev.supplychain.agent.domain.logistics;

import java.util.Objects;

/** Stable, caller-supplied identifier for a carrier domain entity. */
public record CarrierId(String value) {
    public CarrierId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("CarrierId cannot be blank");
        if (value.length() > 128) throw new IllegalArgumentException("CarrierId exceeds 128 characters");
    }
    @Override public String toString() { return value; }
}
