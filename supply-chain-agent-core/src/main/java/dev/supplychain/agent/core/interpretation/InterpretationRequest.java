package dev.supplychain.agent.core.interpretation;

import java.util.Objects;

/** Input to the request-interpretation capability. */
public record InterpretationRequest(
        String originalText,
        String clarificationContext,
        int clarificationRound) {

    public InterpretationRequest {
        originalText = Objects.requireNonNull(originalText, "originalText");
        clarificationContext = clarificationContext == null ? "" : clarificationContext;
        if (clarificationRound < 0) {
            throw new IllegalArgumentException("clarificationRound must not be negative");
        }
    }
}
