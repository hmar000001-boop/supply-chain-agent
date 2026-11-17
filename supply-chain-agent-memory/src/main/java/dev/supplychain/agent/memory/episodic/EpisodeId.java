package dev.supplychain.agent.memory.episodic;

import java.util.Objects;
import java.util.UUID;

public record EpisodeId(String value) {
    public EpisodeId {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("value cannot be blank");
    }
    public static EpisodeId random() { return new EpisodeId(UUID.randomUUID().toString()); }
}
