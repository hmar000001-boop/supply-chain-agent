package dev.supplychain.agent.mechanisms.common;

/** A deterministic mechanism invariant rejected an invalid transition or input. */
public final class MechanismInvariantViolation extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public MechanismInvariantViolation(String message) { super(message); }
}
