package dev.supplychain.agent.memory.common;

import java.util.Objects;

/** Framework- and domain-neutral reference to an authoritative domain entity. */
public record EntityReference(String entityType, String entityId) {
    public EntityReference {
        entityType = require(entityType, "entityType");
        entityId = require(entityId, "entityId");
    }

    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
