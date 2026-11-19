package dev.supplychain.agent.infrastructure.postgres.runtime;

/**
 * Explicit, schema-versioned JSON boundary for durable runtime artifacts.
 * Implementations must use an allow-listed typed mapper and deterministic field ordering.
 * Native Java serialization and unrestricted polymorphic type loading are prohibited.
 */
public interface JsonCodec<T> {
    String encode(T value);
    T decode(String json);
}
