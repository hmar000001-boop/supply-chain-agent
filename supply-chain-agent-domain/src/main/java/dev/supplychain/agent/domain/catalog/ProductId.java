package dev.supplychain.agent.domain.catalog;

import java.util.Objects;

/** Stable, caller-supplied identifier for a product domain entity. */
public record ProductId(String value) {
    public ProductId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("ProductId cannot be blank");
        if (value.length() > 128) throw new IllegalArgumentException("ProductId exceeds 128 characters");
    }
    @Override public String toString() { return value; }
}
