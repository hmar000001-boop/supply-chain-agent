package dev.supplychain.agent.memory.retrieval;

import dev.supplychain.agent.memory.MemoryFixtures;
import dev.supplychain.agent.memory.episodic.EpisodeOutcome;
import dev.supplychain.agent.memory.episodic.EpisodeEvent;
import dev.supplychain.agent.memory.episodic.EpisodicMemoryService;
import dev.supplychain.agent.memory.inmemory.InMemoryEpisodeRepository;
import dev.supplychain.agent.memory.inmemory.InMemoryProceduralMemoryRepository;
import dev.supplychain.agent.memory.inmemory.InMemorySemanticFactRepository;
import dev.supplychain.agent.memory.inmemory.InMemoryWorkingMemoryRepository;
import dev.supplychain.agent.memory.procedural.ProcedureArtifactReference;
import dev.supplychain.agent.memory.procedural.ProcedureKind;
import dev.supplychain.agent.memory.procedural.ProceduralMemoryService;
import dev.supplychain.agent.memory.semantic.SemanticAdmissionPolicy;
import dev.supplychain.agent.memory.semantic.SemanticFactQuery;
import dev.supplychain.agent.memory.semantic.SemanticMemoryService;
import dev.supplychain.agent.memory.working.MemoryValue;
import dev.supplychain.agent.memory.working.WorkingMemoryService;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MemoryRetrievalServiceTest {
    @Test
    void exactRetrievalCombinesAllFourCoalaMemoryResponsibilitiesWithoutB1Scoring() {
        var wmRepository = new InMemoryWorkingMemoryRepository();
        var epRepository = new InMemoryEpisodeRepository();
        var semRepository = new InMemorySemanticFactRepository();
        var procRepository = new InMemoryProceduralMemoryRepository();
        var workingService = new WorkingMemoryService(wmRepository, Duration.ofHours(1));
        var episodicService = new EpisodicMemoryService(epRepository, Duration.ofDays(7));
        var semanticService = new SemanticMemoryService(semRepository, SemanticAdmissionPolicy.conservativeDefault());
        var proceduralService = new ProceduralMemoryService(procRepository);

        var working = workingService.open(MemoryFixtures.SCOPE, MemoryFixtures.NOW);
        workingService.put(working.id(), 0, "active-supplier", new MemoryValue.EntityLink(MemoryFixtures.SUPPLIER),
                List.of(MemoryFixtures.SOURCE_EVIDENCE), MemoryFixtures.NOW.plusSeconds(1));
        var episode = episodicService.record(MemoryFixtures.SCOPE, "Prior supplier incident", "Resolved by alternate source",
                EpisodeOutcome.SUCCESS, MemoryFixtures.NOW.minusSeconds(300), MemoryFixtures.NOW.minusSeconds(60),
                MemoryFixtures.NOW, List.of(MemoryFixtures.SUPPLIER),
                List.of(new EpisodeEvent("resolution", "Alternate source qualified", MemoryFixtures.NOW.minusSeconds(60), List.of())),
                List.of(MemoryFixtures.SOURCE_EVIDENCE), MemoryFixtures.provenance(
                        dev.supplychain.agent.memory.common.ProvenanceSource.AUTHORITATIVE_SYSTEM, "erp-episode", MemoryFixtures.NOW));
        episodicService.verify(episode.id(), 0, "reviewer", MemoryFixtures.REVIEW_EVIDENCE, MemoryFixtures.NOW.plusSeconds(2));
        var fact = MemoryFixtures.proposedCapacity(MemoryFixtures.SCOPE, "300", MemoryFixtures.NOW,
                null, MemoryFixtures.NOW, dev.supplychain.agent.memory.common.ProvenanceSource.AUTHORITATIVE_SYSTEM);
        semanticService.propose(fact);
        semanticService.activateTrusted(fact.id(), 0, "system", MemoryFixtures.NOW.plusSeconds(2));
        var procedure = proceduralService.stage(MemoryFixtures.SCOPE, "supplier.runbook", 1, ProcedureKind.RUNBOOK,
                "Supplier disruption runbook", new ProcedureArtifactReference(URI.create("/runbooks/supplier.md"), "d".repeat(64)),
                MemoryFixtures.provenance(dev.supplychain.agent.memory.common.ProvenanceSource.HUMAN_VERIFIED,
                        "runbook-approval", MemoryFixtures.NOW), MemoryFixtures.NOW, "author", null);
        proceduralService.validate(procedure.id(), 0, "reviewer", MemoryFixtures.NOW.plusSeconds(1));
        proceduralService.activate(procedure.id(), 1, "approver", MemoryFixtures.NOW.plusSeconds(2));

        var retrieval = new MemoryRetrievalService(wmRepository, epRepository, semRepository, procRepository);
        var result = retrieval.retrieve(new MemoryRetrievalQuery(MemoryFixtures.SCOPE, MemoryFixtures.SUPPLIER,
                "capacity.units", working.id(), MemoryFixtures.NOW.plusSeconds(3), MemoryFixtures.NOW.plusSeconds(3), 20));

        assertTrue(result.workingMemory().isPresent());
        assertEquals(1, result.verifiedEpisodes().size());
        assertEquals(1, result.activeSemanticFacts().size());
        assertEquals(1, result.activeProcedures().size());
    }

    @Test
    void episodicRetrievalCanReturnScopeRelevantEpisodesWithoutAnEntityFilter() {
        var episodeRepository = new InMemoryEpisodeRepository();
        var episodic = new EpisodicMemoryService(episodeRepository, Duration.ofDays(7));
        var episode = episodic.record(MemoryFixtures.SCOPE, "General logistics incident", "Carrier delay resolved",
                EpisodeOutcome.SUCCESS, MemoryFixtures.NOW.minusSeconds(300), MemoryFixtures.NOW.minusSeconds(60),
                MemoryFixtures.NOW, List.of(), List.of(), List.of(MemoryFixtures.SOURCE_EVIDENCE),
                MemoryFixtures.provenance(dev.supplychain.agent.memory.common.ProvenanceSource.AUTHORITATIVE_SYSTEM,
                        "carrier-event-7", MemoryFixtures.NOW));
        episodic.verify(episode.id(), 0, "reviewer", MemoryFixtures.REVIEW_EVIDENCE, MemoryFixtures.NOW.plusSeconds(1));

        var retrieval = new MemoryRetrievalService(new InMemoryWorkingMemoryRepository(), episodeRepository,
                new InMemorySemanticFactRepository(), new InMemoryProceduralMemoryRepository());
        var result = retrieval.retrieve(new MemoryRetrievalQuery(MemoryFixtures.SCOPE, null, null, null,
                MemoryFixtures.NOW, MemoryFixtures.NOW.plusSeconds(2), 10));

        assertEquals(1, result.verifiedEpisodes().size());
    }

    @Test
    void workingMemoryReferenceCannotBeReadAcrossScopes() {
        var repository = new InMemoryWorkingMemoryRepository();
        var workingService = new WorkingMemoryService(repository, Duration.ofHours(1));
        var working = workingService.open(MemoryFixtures.SCOPE, MemoryFixtures.NOW);
        var retrieval = new MemoryRetrievalService(repository, new InMemoryEpisodeRepository(),
                new InMemorySemanticFactRepository(), new InMemoryProceduralMemoryRepository());
        assertThrows(SecurityException.class, () -> retrieval.retrieve(new MemoryRetrievalQuery(
                MemoryFixtures.OTHER_SCOPE, null, null, working.id(), MemoryFixtures.NOW, MemoryFixtures.NOW, 5)));
    }
}
