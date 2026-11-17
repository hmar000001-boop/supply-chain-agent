package dev.supplychain.agent.domain.procurement;

import java.util.Objects;

/** Stable, caller-supplied identifier for a purchaseorder domain entity. */
public record PurchaseOrderId(String value) {
    public PurchaseOrderId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("PurchaseOrderId cannot be blank");
        if (value.length() > 128) throw new IllegalArgumentException("PurchaseOrderId exceeds 128 characters");
    }
    @Override public String toString() { return value; }
}
