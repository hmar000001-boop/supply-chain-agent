package dev.supplychain.agent.domain.logistics;

import java.util.Objects;

/** Stable, caller-supplied identifier for a shipment domain entity. */
public record ShipmentId(String value) {
    public ShipmentId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("ShipmentId cannot be blank");
        if (value.length() > 128) throw new IllegalArgumentException("ShipmentId exceeds 128 characters");
    }
    @Override public String toString() { return value; }
}
