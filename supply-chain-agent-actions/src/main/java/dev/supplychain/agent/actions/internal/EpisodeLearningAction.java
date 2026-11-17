package dev.supplychain.agent.actions.internal;

import dev.supplychain.agent.actions.api.*;
import dev.supplychain.agent.memory.common.Provenance;
import dev.supplychain.agent.memory.episodic.Episode;
import dev.supplychain.agent.memory.episodic.EpisodicMemoryService;
import java.util.Objects;
import java.util.Set;

/** Records an unverified episode. It cannot verify or promote its own learning record. */
public final class EpisodeLearningAction implements ActionHandler<RecordEpisodeRequest, Episode> {
    public static final ActionId ID = new ActionId("coala.memory.episode.record");
    private static final ActionDescriptor DESCRIPTOR = new ActionDescriptor(ID, "Record an episode",
            "Append an unverified experience record with explicit provenance and evidence", ActionCategory.INTERNAL,
            ActionEffect.MEMORY_WRITE, Capability.MEMORY_RECORD_EPISODE, true);
    private final EpisodicMemoryService service;
    public EpisodeLearningAction(EpisodicMemoryService service) { this.service = Objects.requireNonNull(service, "service"); }
    @Override public ActionDescriptor descriptor() { return DESCRIPTOR; }
    @Override public Class<RecordEpisodeRequest> inputType() { return RecordEpisodeRequest.class; }
    @Override public Class<Episode> outputType() { return Episode.class; }
    @Override public Set<ResourceReference> resources(RecordEpisodeRequest input) {
        return Set.of(new ResourceReference("memory-scope", input.scopeId().value()));
    }
    @Override public String requestFingerprint(RecordEpisodeRequest input) { return input.canonical(); }
    @Override public Episode execute(ActionContext context, RecordEpisodeRequest input) {
        var provenance = new Provenance(input.provenanceSource(), input.provenanceSourceId(),
                input.observedAt(), context.now(), input.sourceVersion());
        return service.record(input.scopeId(), input.title(), input.summary(), input.outcome(),
                input.startedAt(), input.completedAt(), context.now(), input.entities(), input.events(),
                input.evidence(), provenance);
    }
    @Override public String resultReference(Episode output) { return "episode:" + output.id().value() + ":RECORDED"; }
}
