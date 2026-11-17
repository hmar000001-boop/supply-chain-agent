package dev.supplychain.agent.memory.episodic;

import dev.supplychain.agent.memory.common.EvidenceReference;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record EpisodeEvent(String eventType, String description, Instant occurredAt,
                           List<EvidenceReference> evidence) {
    public EpisodeEvent {
        eventType = require(eventType, "eventType");
        description = require(description, "description");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
    }
    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
