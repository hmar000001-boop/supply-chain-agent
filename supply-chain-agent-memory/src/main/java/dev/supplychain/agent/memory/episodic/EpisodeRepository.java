package dev.supplychain.agent.memory.episodic;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EpisodeRepository {
    Episode append(Episode episode);
    /** Atomically returns the existing episode for a scope/evidence idempotency reference or appends the supplied one. */
    Episode appendIdempotent(Episode episode, dev.supplychain.agent.memory.common.EvidenceReference idempotencyReference);
    Optional<Episode> findById(EpisodeId id);
    Episode compareAndSet(EpisodeId id, long expectedVersion, Episode replacement);
    List<Episode> findVerified(EpisodeQuery query);
    int deleteExpiredUnverified(Instant now);
}
