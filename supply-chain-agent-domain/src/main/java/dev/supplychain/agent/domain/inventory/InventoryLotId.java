package dev.supplychain.agent.domain.inventory;

import java.util.Objects;

/** Stable, caller-supplied identifier for a inventorylot domain entity. */
public record InventoryLotId(String value) {
    public InventoryLotId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("InventoryLotId cannot be blank");
        if (value.length() > 128) throw new IllegalArgumentException("InventoryLotId exceeds 128 characters");
    }
    @Override public String toString() { return value; }
}
