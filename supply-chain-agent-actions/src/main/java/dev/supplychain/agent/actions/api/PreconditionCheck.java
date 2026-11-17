package dev.supplychain.agent.actions.api;

import java.util.Objects;

public record PreconditionCheck(boolean allowed, String code, String reason) {
    public PreconditionCheck {
        code = require(code, "code");
        reason = require(reason, "reason");
    }
    public static PreconditionCheck pass() { return new PreconditionCheck(true, "OK", "All action preconditions passed"); }
    public static PreconditionCheck reject(String code, String reason) { return new PreconditionCheck(false, code, reason); }
    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
