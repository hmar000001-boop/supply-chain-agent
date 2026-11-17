package dev.supplychain.agent.domain.supplier;

import java.util.Objects;

/** Stable, caller-supplied identifier for a supplier domain entity. */
public record SupplierId(String value) {
    public SupplierId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("SupplierId cannot be blank");
        if (value.length() > 128) throw new IllegalArgumentException("SupplierId exceeds 128 characters");
    }
    @Override public String toString() { return value; }
}
