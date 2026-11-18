package dev.supplychain.agent.memory.inmemory;

import dev.supplychain.agent.memory.common.MemoryConflictException;
import dev.supplychain.agent.memory.common.MemoryNotFoundException;
import dev.supplychain.agent.memory.common.MemoryVersionConflictException;
import dev.supplychain.agent.memory.episodic.Episode;
import dev.supplychain.agent.memory.episodic.EpisodeId;
import dev.supplychain.agent.memory.episodic.EpisodeQuery;
import dev.supplychain.agent.memory.episodic.EpisodeRepository;
import dev.supplychain.agent.memory.episodic.EpisodeStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe reference adapter. Verified episodes are retained; only expired unverified records are purged. */
public final class InMemoryEpisodeRepository implements EpisodeRepository {
    private final ConcurrentHashMap<EpisodeId, Episode> episodes = new ConcurrentHashMap<>();

    @Override
    public Episode append(Episode episode) {
        if (episodes.putIfAbsent(episode.id(), episode) != null) {
            throw new MemoryConflictException("Episode already exists: " + episode.id().value());
        }
        return episode;
    }

    @Override
    public synchronized Episode appendIdempotent(Episode episode,
            dev.supplychain.agent.memory.common.EvidenceReference idempotencyReference) {
        java.util.Objects.requireNonNull(episode, "episode");
        java.util.Objects.requireNonNull(idempotencyReference, "idempotencyReference");
        if (!episode.evidence().contains(idempotencyReference)) {
            throw new IllegalArgumentException("episode must retain its idempotency evidence reference");
        }
        for (Episode existing : episodes.values()) {
            if (existing.scopeId().equals(episode.scopeId()) && existing.evidence().contains(idempotencyReference)) {
                if (!sameIdempotentPayload(existing, episode)) {
                    throw new MemoryConflictException("episode idempotency reference was reused with a different outcome payload");
                }
                return existing;
            }
        }
        if (episodes.putIfAbsent(episode.id(), episode) != null) {
            throw new MemoryConflictException("Episode already exists: " + episode.id().value());
        }
        return episode;
    }

    private static boolean sameIdempotentPayload(Episode existing, Episode proposed) {
        var oldSource = existing.provenance();
        var newSource = proposed.provenance();
        return existing.title().equals(proposed.title())
                && existing.summary().equals(proposed.summary())
                && existing.outcome() == proposed.outcome()
                && existing.startedAt().equals(proposed.startedAt())
                && existing.completedAt().equals(proposed.completedAt())
                && existing.entities().equals(proposed.entities())
                && existing.events().equals(proposed.events())
                && payloadEvidence(existing).equals(payloadEvidence(proposed))
                && oldSource.source() == newSource.source()
                && oldSource.sourceId().equals(newSource.sourceId())
                && oldSource.observedAt().equals(newSource.observedAt())
                && Objects.equals(oldSource.sourceVersion(), newSource.sourceVersion());
    }

    private static List<dev.supplychain.agent.memory.common.EvidenceReference> payloadEvidence(Episode episode) {
        // Verification appends its review reference to Episode.evidence(); it is lifecycle metadata,
        // not a change to the original idempotent observation payload.
        if ((episode.status() != dev.supplychain.agent.memory.episodic.EpisodeStatus.VERIFIED
                && episode.status() != dev.supplychain.agent.memory.episodic.EpisodeStatus.SUPERSEDED)
                || episode.reviewEvidence() == null || !episode.evidence().contains(episode.reviewEvidence())) {
            return episode.evidence();
        }
        return episode.evidence().stream().filter(reference -> !reference.equals(episode.reviewEvidence())).toList();
    }

    @Override
    public Optional<Episode> findById(EpisodeId id) { return Optional.ofNullable(episodes.get(id)); }

    @Override
    public Episode compareAndSet(EpisodeId id, long expectedVersion, Episode replacement) {
        return episodes.compute(id, (key, current) -> {
            if (current == null) throw new MemoryNotFoundException("Episode not found: " + id.value());
            if (current.version() != expectedVersion) {
                throw new MemoryVersionConflictException("Episode version changed from " + expectedVersion + " to " + current.version());
            }
            if (!replacement.id().equals(id) || replacement.version() != expectedVersion + 1
                    || !replacement.scopeId().equals(current.scopeId())) {
                throw new IllegalArgumentException("replacement must preserve id/scope and increment version once");
            }
            return replacement;
        });
    }

    @Override
    public List<Episode> findVerified(EpisodeQuery query) {
        return episodes.values().stream()
                .filter(e -> e.scopeId().equals(query.scopeId()))
                .filter(e -> e.knownAsVerifiedAt(query.knownAt()))
                .filter(e -> query.entity() == null || e.entities().contains(query.entity()))
                .sorted(Comparator.comparing(Episode::completedAt).reversed().thenComparing(e -> e.id().value()))
                .limit(query.limit()).toList();
    }

    @Override
    public int deleteExpiredUnverified(Instant now) {
        int before = episodes.size();
        episodes.entrySet().removeIf(entry -> {
            Episode episode = entry.getValue();
            return episode.status() == EpisodeStatus.RECORDED
                    && episode.retentionUntil() != null && !episode.retentionUntil().isAfter(now);
        });
        return before - episodes.size();
    }
}
