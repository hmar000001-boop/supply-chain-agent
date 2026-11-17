package dev.supplychain.agent.actions;

import dev.supplychain.agent.actions.external.grounding.InMemorySupplyChainSimulationWorld;
import dev.supplychain.agent.actions.external.perception.SnapshotSupplyChainPerceptionAdapter;
import dev.supplychain.agent.memory.inmemory.*;
import dev.supplychain.agent.memory.episodic.EpisodicMemoryService;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalService;
import dev.supplychain.agent.memory.working.WorkingMemoryService;
import dev.supplychain.agent.simulator.SupplyChainSimulator;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupplyChainActionCatalogTest {
    @Test
    void catalogRegistersInternalPerceptionAndGroundingActionsWithoutFrameworkTypes() {
        var workingRepo = new InMemoryWorkingMemoryRepository();
        var episodeRepo = new InMemoryEpisodeRepository();
        var semanticRepo = new InMemorySemanticFactRepository();
        var proceduralRepo = new InMemoryProceduralMemoryRepository();
        var retrieval = new MemoryRetrievalService(workingRepo, episodeRepo, semanticRepo, proceduralRepo);
        var working = new WorkingMemoryService(workingRepo, Duration.ofHours(1));
        var episodic = new EpisodicMemoryService(episodeRepo, Duration.ofDays(7));
        var world = ActionTestFixtures.world();
        var perception = new SnapshotSupplyChainPerceptionAdapter(() -> world);
        var simulation = new InMemorySupplyChainSimulationWorld(world, new SupplyChainSimulator());

        var registry = SupplyChainActionCatalog.create(retrieval, working, episodic,
                request -> new dev.supplychain.agent.actions.internal.ReasoningAssessment(
                        "No model configured", java.util.List.of(), java.util.List.of(), java.util.List.of(),
                        java.util.List.of(), java.util.List.of("Reasoning provider is not configured"),
                        dev.supplychain.agent.actions.internal.ReasoningConfidence.UNASSESSED,
                        "No model call was made"), perception, simulation);

        assertEquals(14, registry.descriptors().size());
        assertEquals(4, registry.descriptors().stream().filter(d -> d.category() == dev.supplychain.agent.actions.api.ActionCategory.INTERNAL).count());
        assertEquals(6, registry.descriptors().stream().filter(d -> d.category() == dev.supplychain.agent.actions.api.ActionCategory.PERCEPTION).count());
        assertEquals(4, registry.descriptors().stream().filter(d -> d.category() == dev.supplychain.agent.actions.api.ActionCategory.GROUNDING).count());
        assertTrue(registry.descriptors().stream().filter(d -> d.effect() != dev.supplychain.agent.actions.api.ActionEffect.READ_ONLY)
                .allMatch(dev.supplychain.agent.actions.api.ActionDescriptor::idempotencyRequired));
    }
}
