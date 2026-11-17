package dev.supplychain.agent.actions.api;

import java.util.Objects;

public record ActionDescriptor(ActionId id, String name, String description, ActionCategory category,
                               ActionEffect effect, Capability requiredCapability, boolean idempotencyRequired) {
    public ActionDescriptor {
        id = Objects.requireNonNull(id, "id");
        name = require(name, "name");
        description = require(description, "description");
        category = Objects.requireNonNull(category, "category");
        effect = Objects.requireNonNull(effect, "effect");
        requiredCapability = Objects.requireNonNull(requiredCapability, "requiredCapability");
        if (effect != ActionEffect.READ_ONLY && !idempotencyRequired) {
            throw new IllegalArgumentException("state-changing actions must require an idempotency key");
        }
        if (category == ActionCategory.PERCEPTION && effect != ActionEffect.READ_ONLY) {
            throw new IllegalArgumentException("perception actions must be read-only; event ingestion is a separate trusted boundary");
        }
        if (category == ActionCategory.GROUNDING && effect != ActionEffect.DOMAIN_WRITE) {
            throw new IllegalArgumentException("grounding actions must declare a domain write");
        }
    }
    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
