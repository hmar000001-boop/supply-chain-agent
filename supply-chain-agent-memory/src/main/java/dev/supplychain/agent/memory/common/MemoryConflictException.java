package dev.supplychain.agent.memory.common;

public final class MemoryConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public MemoryConflictException(String message) {
        super(message);
    }
}
