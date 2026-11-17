package dev.supplychain.agent.actions.internal;

import dev.supplychain.agent.memory.common.EntityReference;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.ProvenanceSource;
import dev.supplychain.agent.memory.episodic.EpisodeEvent;
import dev.supplychain.agent.memory.episodic.EpisodeOutcome;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record RecordEpisodeRequest(MemoryScopeId scopeId, String title, String summary, EpisodeOutcome outcome,
                                   Instant startedAt, Instant completedAt, List<EntityReference> entities,
                                   List<EpisodeEvent> events, List<EvidenceReference> evidence,
                                   ProvenanceSource provenanceSource, String provenanceSourceId,
                                   Instant observedAt, String sourceVersion) {
    public RecordEpisodeRequest {
        scopeId = Objects.requireNonNull(scopeId, "scopeId");
        title = require(title, "title");
        summary = require(summary, "summary");
        outcome = Objects.requireNonNull(outcome, "outcome");
        startedAt = Objects.requireNonNull(startedAt, "startedAt");
        completedAt = Objects.requireNonNull(completedAt, "completedAt");
        if (completedAt.isBefore(startedAt)) throw new IllegalArgumentException("completedAt precedes startedAt");
        entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        provenanceSource = Objects.requireNonNull(provenanceSource, "provenanceSource");
        provenanceSourceId = require(provenanceSourceId, "provenanceSourceId");
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
        sourceVersion = sourceVersion == null || sourceVersion.isBlank() ? null : sourceVersion.trim();
    }
    public String canonical() {
        return scopeId.value() + "|" + title + "|" + summary + "|" + outcome + "|" + startedAt + "|" + completedAt
                + "|entities=" + entities + "|events=" + events + "|evidence=" + evidence
                + "|source=" + provenanceSource + "|sourceId=" + provenanceSourceId
                + "|observedAt=" + observedAt + "|sourceVersion=" + sourceVersion;
    }
    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
