package dev.supplychain.agent.memory.common;

public final class MemoryNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public MemoryNotFoundException(String message) {
        super(message);
    }
}
