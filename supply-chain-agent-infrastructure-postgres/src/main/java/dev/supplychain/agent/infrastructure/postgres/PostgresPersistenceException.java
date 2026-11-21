package dev.supplychain.agent.infrastructure.postgres;

public final class PostgresPersistenceException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public PostgresPersistenceException(String message) { super(message); }
    public PostgresPersistenceException(String message, Throwable cause) { super(message, cause); }
}
