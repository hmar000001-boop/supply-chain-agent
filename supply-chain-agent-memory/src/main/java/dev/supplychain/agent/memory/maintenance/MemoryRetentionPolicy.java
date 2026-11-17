package dev.supplychain.agent.memory.maintenance;

import java.time.Duration;
import java.util.Objects;

/** Retention is explicit and type-specific; verified episodes and procedure history are not auto-purged. */
public record MemoryRetentionPolicy(
        Duration workingMemoryTtl,
        Duration unverifiedEpisodeRetention,
        Duration supersededFactRetention) {
    public MemoryRetentionPolicy {
        workingMemoryTtl = positive(workingMemoryTtl, "workingMemoryTtl");
        unverifiedEpisodeRetention = positive(unverifiedEpisodeRetention, "unverifiedEpisodeRetention");
        supersededFactRetention = positive(supersededFactRetention, "supersededFactRetention");
    }
    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
}
