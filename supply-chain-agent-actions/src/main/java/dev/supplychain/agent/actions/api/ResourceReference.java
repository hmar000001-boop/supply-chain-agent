package dev.supplychain.agent.actions.api;

import java.util.Objects;

public record ResourceReference(String type, String id) implements Comparable<ResourceReference> {
    public ResourceReference {
        type = require(type, "type");
        id = require(id, "id");
    }
    public String canonical() { return type + ":" + id; }
    @Override public int compareTo(ResourceReference other) { return canonical().compareTo(other.canonical()); }
    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
