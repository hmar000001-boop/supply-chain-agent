package dev.supplychain.agent.domain.orders;

import java.util.Objects;

/** Stable, caller-supplied identifier for a orderline domain entity. */
public record OrderLineId(String value) {
    public OrderLineId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("OrderLineId cannot be blank");
        if (value.length() > 128) throw new IllegalArgumentException("OrderLineId exceeds 128 characters");
    }
    @Override public String toString() { return value; }
}
