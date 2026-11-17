package dev.supplychain.agent.memory.maintenance;

import dev.supplychain.agent.memory.episodic.EpisodeRepository;
import dev.supplychain.agent.memory.semantic.SemanticFactRepository;
import dev.supplychain.agent.memory.working.WorkingMemoryRepository;
import java.time.Instant;
import java.util.Objects;

/** Applies bounded retention only to explicitly disposable lifecycle states. */
public final class MemoryMaintenanceService {
    private final WorkingMemoryRepository working;
    private final EpisodeRepository episodic;
    private final SemanticFactRepository semantic;
    private final MemoryRetentionPolicy policy;

    public MemoryMaintenanceService(WorkingMemoryRepository working, EpisodeRepository episodic,
                                    SemanticFactRepository semantic, MemoryRetentionPolicy policy) {
        this.working = Objects.requireNonNull(working, "working");
        this.episodic = Objects.requireNonNull(episodic, "episodic");
        this.semantic = Objects.requireNonNull(semantic, "semantic");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public MemoryMaintenanceResult purgeExpired(Instant now) {
        Objects.requireNonNull(now, "now");
        return new MemoryMaintenanceResult(
                working.deleteExpired(now),
                episodic.deleteExpiredUnverified(now),
                semantic.deleteExpiredSuperseded(now.minus(policy.supersededFactRetention())));
    }
}
