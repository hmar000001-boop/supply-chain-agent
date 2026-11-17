package dev.supplychain.agent.domain.recovery;

import java.util.Objects;

/** Stable, caller-supplied identifier for a recoveryobjective domain entity. */
public record RecoveryObjectiveId(String value) {
    public RecoveryObjectiveId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("RecoveryObjectiveId cannot be blank");
        if (value.length() > 128) throw new IllegalArgumentException("RecoveryObjectiveId exceeds 128 characters");
    }
    @Override public String toString() { return value; }
}
