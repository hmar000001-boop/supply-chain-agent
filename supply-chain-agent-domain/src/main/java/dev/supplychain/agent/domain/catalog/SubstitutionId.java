package dev.supplychain.agent.domain.catalog;

import java.util.Objects;

/** Stable, caller-supplied identifier for a substitution domain entity. */
public record SubstitutionId(String value) {
    public SubstitutionId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("SubstitutionId cannot be blank");
        if (value.length() > 128) throw new IllegalArgumentException("SubstitutionId exceeds 128 characters");
    }
    @Override public String toString() { return value; }
}
