package dev.supplychain.agent.planning.session;

public final class PlannerCheckpointConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public PlannerCheckpointConflictException(String message) { super(message); }
}
