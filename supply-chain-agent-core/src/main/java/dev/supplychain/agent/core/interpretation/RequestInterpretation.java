package dev.supplychain.agent.core.interpretation;

import java.util.Objects;

/** Validated interpretation returned to the application layer. */
public record RequestInterpretation(
        InterpretationOutcome outcome,
        String normalizedIntent,
        String summary) {

    public RequestInterpretation {
        outcome = Objects.requireNonNull(outcome, "outcome");
        normalizedIntent = normalizedIntent == null ? "" : normalizedIntent;
        summary = summary == null ? "" : summary;
    }
}
