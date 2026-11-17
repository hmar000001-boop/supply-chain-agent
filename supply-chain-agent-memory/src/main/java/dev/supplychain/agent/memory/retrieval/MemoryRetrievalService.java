package dev.supplychain.agent.memory.retrieval;

import dev.supplychain.agent.memory.common.MemoryNotFoundException;
import dev.supplychain.agent.memory.episodic.EpisodeQuery;
import dev.supplychain.agent.memory.episodic.EpisodeRepository;
import dev.supplychain.agent.memory.procedural.ProceduralMemoryRepository;
import dev.supplychain.agent.memory.semantic.SemanticFactQuery;
import dev.supplychain.agent.memory.semantic.SemanticFactRepository;
import dev.supplychain.agent.memory.working.WorkingMemoryRepository;
import java.util.Objects;
import java.util.Optional;

/** Cross-memory exact retrieval. It deliberately does not implement B1 activation scoring. */
public final class MemoryRetrievalService {
    private final WorkingMemoryRepository working;
    private final EpisodeRepository episodic;
    private final SemanticFactRepository semantic;
    private final ProceduralMemoryRepository procedural;

    public MemoryRetrievalService(WorkingMemoryRepository working, EpisodeRepository episodic,
                                  SemanticFactRepository semantic, ProceduralMemoryRepository procedural) {
        this.working = Objects.requireNonNull(working, "working");
        this.episodic = Objects.requireNonNull(episodic, "episodic");
        this.semantic = Objects.requireNonNull(semantic, "semantic");
        this.procedural = Objects.requireNonNull(procedural, "procedural");
    }

    public MemoryRetrievalResult retrieve(MemoryRetrievalQuery query) {
        var activeWorking = Optional.<dev.supplychain.agent.memory.working.WorkingMemorySnapshot>empty();
        if (query.workingMemoryId() != null) {
            var snapshot = working.findById(query.workingMemoryId()).orElseThrow(() ->
                    new MemoryNotFoundException("Working memory not found: " + query.workingMemoryId().value()));
            if (!snapshot.scopeId().equals(query.scopeId())) {
                throw new SecurityException("working-memory scope does not match retrieval scope");
            }
            if (!snapshot.expiresAt().isAfter(query.knownAt())) {
                throw new MemoryNotFoundException("Working memory has expired at requested valid time");
            }
            activeWorking = Optional.of(snapshot);
        }
        var episodes = episodic.findVerified(
                new EpisodeQuery(query.scopeId(), query.entity(), query.knownAt(), query.limitPerType()));
        var facts = query.entity() == null ? java.util.List.<dev.supplychain.agent.memory.semantic.SemanticFact>of()
                : semantic.findActive(new SemanticFactQuery(query.scopeId(), query.entity(),
                        query.semanticPredicate(), query.validAt(), query.knownAt(), query.limitPerType()));
        var procedures = procedural.findActiveAt(query.scopeId(), query.knownAt(), query.limitPerType());
        return new MemoryRetrievalResult(activeWorking, episodes, facts, procedures);
    }
}
