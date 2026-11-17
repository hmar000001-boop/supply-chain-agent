package dev.supplychain.agent.actions.api;

import java.util.Objects;

public record ActionId(String value) {
    public ActionId {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty() || value.length() > 160) throw new IllegalArgumentException("ActionId must contain 1..160 characters");
    }
    @Override public String toString() { return value; }
}
