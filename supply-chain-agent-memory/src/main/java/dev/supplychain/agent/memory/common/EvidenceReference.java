package dev.supplychain.agent.memory.common;

import java.util.Objects;

/** Stable reference to evidence; memory records do not copy or redefine source truth. */
public record EvidenceReference(String evidenceType, String evidenceId) {
    public EvidenceReference {
        evidenceType = require(evidenceType, "evidenceType");
        evidenceId = require(evidenceId, "evidenceId");
    }

    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
