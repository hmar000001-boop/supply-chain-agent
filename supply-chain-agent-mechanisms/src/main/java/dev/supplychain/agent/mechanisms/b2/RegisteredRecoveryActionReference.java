package dev.supplychain.agent.mechanisms.b2;

import dev.supplychain.agent.actions.api.ActionEffect;
import dev.supplychain.agent.actions.api.Capability;
import java.util.Objects;

/** Immutable reference to a host-registered action descriptor; it contains no executable payload. */
public record RegisteredRecoveryActionReference(String actionId, Capability requiredCapability,
                                                 ActionEffect effect, String descriptorFingerprint) {
    public RegisteredRecoveryActionReference {
        actionId = required(actionId, "actionId");
        requiredCapability = Objects.requireNonNull(requiredCapability, "requiredCapability");
        effect = Objects.requireNonNull(effect, "effect");
        descriptorFingerprint = required(descriptorFingerprint, "descriptorFingerprint");
        if (!descriptorFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("descriptorFingerprint must be a SHA-256 hex digest");
        }
    }
    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return normalized;
    }
}
