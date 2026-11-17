package dev.supplychain.agent.actions.api;

import java.util.Objects;

public record IdempotencyLedgerKey(String principalId, ActionId actionId, String key) {
    public IdempotencyLedgerKey {
        principalId = require(principalId, "principalId");
        actionId = Objects.requireNonNull(actionId, "actionId");
        key = require(key, "key");
    }
    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
