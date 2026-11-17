package dev.supplychain.agent.memory.episodic;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EpisodeRepository {
    Episode append(Episode episode);
    Optional<Episode> findById(EpisodeId id);
    Episode compareAndSet(EpisodeId id, long expectedVersion, Episode replacement);
    List<Episode> findVerified(EpisodeQuery query);
    int deleteExpiredUnverified(Instant now);
}
