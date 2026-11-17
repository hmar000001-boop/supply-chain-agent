package dev.supplychain.agent.domain.inventory;

import java.util.Objects;

/** Stable, caller-supplied identifier for a inventorylocation domain entity. */
public record InventoryLocationId(String value) {
    public InventoryLocationId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("InventoryLocationId cannot be blank");
        if (value.length() > 128) throw new IllegalArgumentException("InventoryLocationId exceeds 128 characters");
    }
    @Override public String toString() { return value; }
}
