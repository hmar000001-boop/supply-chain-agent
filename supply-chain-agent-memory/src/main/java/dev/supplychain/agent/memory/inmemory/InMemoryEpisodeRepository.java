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
