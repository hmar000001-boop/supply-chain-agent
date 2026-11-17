package dev.supplychain.agent.domain.inventory;

import java.util.Objects;

/** Stable, caller-supplied identifier for a reservation domain entity. */
public record ReservationId(String value) {
    public ReservationId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("ReservationId cannot be blank");
        if (value.length() > 128) throw new IllegalArgumentException("ReservationId exceeds 128 characters");
    }
    @Override public String toString() { return value; }
}
