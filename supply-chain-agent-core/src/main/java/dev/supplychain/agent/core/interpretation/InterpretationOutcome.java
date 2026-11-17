package dev.supplychain.agent.core.interpretation;

/** A bounded, machine-readable interpretation outcome. */
public enum InterpretationOutcome {
    READY,
    NEEDS_CLARIFICATION,
    OUT_OF_SCOPE,
    UNKNOWN
}
