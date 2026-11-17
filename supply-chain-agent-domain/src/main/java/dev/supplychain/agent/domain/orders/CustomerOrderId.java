package dev.supplychain.agent.domain.orders;

import java.util.Objects;

/** Stable, caller-supplied identifier for a customerorder domain entity. */
public record CustomerOrderId(String value) {
    public CustomerOrderId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("CustomerOrderId cannot be blank");
        if (value.length() > 128) throw new IllegalArgumentException("CustomerOrderId exceeds 128 characters");
    }
    @Override public String toString() { return value; }
}
