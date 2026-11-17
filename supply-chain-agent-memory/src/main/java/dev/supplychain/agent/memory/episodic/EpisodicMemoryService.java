package dev.supplychain.agent.memory.episodic;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryNotFoundException;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.Provenance;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class EpisodicMemoryService {
    private final EpisodeRepository repository;
    private final java.time.Duration unverifiedRetention;

    public EpisodicMemoryService(EpisodeRepository repository, java.time.Duration unverifiedRetention) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.unverifiedRetention = Objects.requireNonNull(unverifiedRetention, "unverifiedRetention");
        if (unverifiedRetention.isNegative() || unverifiedRetention.isZero()) {
            throw new IllegalArgumentException("unverifiedRetention must be positive");
        }
    }

    public EpisodicMemoryService(EpisodeRepository repository,
                                 dev.supplychain.agent.memory.maintenance.MemoryRetentionPolicy policy) {
        this(repository, Objects.requireNonNull(policy, "policy").unverifiedEpisodeRetention());
    }

    public Episode record(MemoryScopeId scope, String title, String summary, EpisodeOutcome outcome,
                          Instant startedAt, Instant completedAt, Instant recordedAt,
                          List<dev.supplychain.agent.memory.common.EntityReference> entities,
                          List<EpisodeEvent> events, List<EvidenceReference> evidence, Provenance provenance) {
        Objects.requireNonNull(provenance, "provenance");
        List<EvidenceReference> suppliedEvidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        List<EpisodeEvent> suppliedEvents = List.copyOf(Objects.requireNonNull(events, "events"));
        var allEvidence = new java.util.ArrayList<>(suppliedEvidence);
        suppliedEvents.forEach(event -> allEvidence.addAll(event.evidence()));
        List<EvidenceReference> distinctEvidence = allEvidence.stream().distinct().toList();
        if (provenance.isModelDerived() && distinctEvidence.isEmpty()) {
            throw new IllegalArgumentException("model-derived episode requires supporting evidence before recording");
        }
        Episode episode = Episode.recorded(EpisodeId.random(), scope, title, summary, outcome,
                startedAt, completedAt, recordedAt, recordedAt.plus(unverifiedRetention), entities,
                suppliedEvents, distinctEvidence, provenance);
        return repository.append(episode);
    }

    public Episode verify(EpisodeId id, long expectedVersion, String verifier,
                          EvidenceReference verificationEvidence, Instant at) {
        Episode current = load(id);
        requireVersion(current, expectedVersion);
        return repository.compareAndSet(id, expectedVersion, current.verify(verifier, verificationEvidence, at));
    }

    public Episode reject(EpisodeId id, long expectedVersion, String reviewer, EvidenceReference reviewEvidence, Instant at) {
        Episode current = load(id);
        requireVersion(current, expectedVersion);
        return repository.compareAndSet(id, expectedVersion, current.reject(reviewer, reviewEvidence, at));
    }

    public Episode supersede(EpisodeId id, long expectedVersion, Instant at) {
        Episode current = load(id);
        requireVersion(current, expectedVersion);
        return repository.compareAndSet(id, expectedVersion, current.supersede(at));
    }

    public List<Episode> retrieveVerified(EpisodeQuery query) { return repository.findVerified(query); }

    public Episode load(EpisodeId id) {
        return repository.findById(id).orElseThrow(() -> new MemoryNotFoundException("Episode not found: " + id.value()));
    }

    private static void requireVersion(Episode episode, long expected) {
        if (episode.version() != expected) {
            throw new dev.supplychain.agent.memory.common.MemoryVersionConflictException(
                    "Episode version is " + episode.version() + ", expected " + expected);
        }
    }
}
