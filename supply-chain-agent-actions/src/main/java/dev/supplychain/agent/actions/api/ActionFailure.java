package dev.supplychain.agent.actions.api;

import java.util.Objects;

public record ActionFailure(ActionFailureCode code, String message, boolean retryable, boolean outcomeUnknown) {
    public ActionFailure {
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message").trim();
        if (message.isEmpty()) throw new IllegalArgumentException("message cannot be blank");
        if (outcomeUnknown && retryable) throw new IllegalArgumentException("unknown outcomes must not be blindly retried");
    }
}
