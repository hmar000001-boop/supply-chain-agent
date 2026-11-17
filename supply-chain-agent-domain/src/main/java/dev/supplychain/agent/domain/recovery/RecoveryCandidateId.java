package dev.supplychain.agent.domain.recovery;

import java.util.Objects;

/** Stable, caller-supplied identifier for a recoverycandidate domain entity. */
public record RecoveryCandidateId(String value) {
    public RecoveryCandidateId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("RecoveryCandidateId cannot be blank");
        if (value.length() > 128) throw new IllegalArgumentException("RecoveryCandidateId exceeds 128 characters");
    }
    @Override public String toString() { return value; }
}
