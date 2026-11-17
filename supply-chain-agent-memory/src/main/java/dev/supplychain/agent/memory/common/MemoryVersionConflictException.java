package dev.supplychain.agent.memory.common;

public final class MemoryVersionConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public MemoryVersionConflictException(String message) {
        super(message);
    }
}
