package dev.supplychain.agent.domain.logistics;

import java.util.Objects;

/** Stable, caller-supplied identifier for a route domain entity. */
public record RouteId(String value) {
    public RouteId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("RouteId cannot be blank");
        if (value.length() > 128) throw new IllegalArgumentException("RouteId exceeds 128 characters");
    }
    @Override public String toString() { return value; }
}
