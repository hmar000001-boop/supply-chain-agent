package dev.supplychain.agent.actions;

import dev.supplychain.agent.actions.api.ActionRegistry;
import dev.supplychain.agent.actions.external.grounding.SupplyChainGroundingActions;
import dev.supplychain.agent.actions.external.grounding.SupplyChainSimulationWorld;
import dev.supplychain.agent.actions.external.perception.SupplyChainPerceptionActions;
import dev.supplychain.agent.actions.external.perception.SupplyChainPerceptionPort;
import dev.supplychain.agent.actions.internal.EpisodeLearningAction;
import dev.supplychain.agent.actions.internal.MemoryRetrievalAction;
import dev.supplychain.agent.actions.internal.ReasoningAction;
import dev.supplychain.agent.actions.internal.ReasoningProvider;
import dev.supplychain.agent.actions.internal.WorkingMemoryUpdateAction;
import dev.supplychain.agent.actions.internal.learning.LearningActionCatalog;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.episodic.EpisodicMemoryService;
import dev.supplychain.agent.memory.procedural.ProceduralMemoryService;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalService;
import dev.supplychain.agent.memory.semantic.SemanticMemoryService;
import dev.supplychain.agent.memory.working.WorkingMemoryService;
import java.util.Objects;

/** Composition helper for the complete M3 action catalog; no framework types cross this boundary. */
public final class SupplyChainActionCatalog {
    private SupplyChainActionCatalog() { }

    /** Backward-compatible non-durable catalog used by small fixtures and adapters. */
    public static ActionRegistry create(MemoryRetrievalService retrieval, WorkingMemoryService working,
                                        EpisodicMemoryService episodic, ReasoningProvider reasoning,
                                        SupplyChainPerceptionPort perception, SupplyChainSimulationWorld world) {
        return createCatalog(retrieval, working, episodic, null, null, null, reasoning, perception, world);
    }

    /**
     * Complete application catalog. The durable host supplies its fixed memory scope so learning
     * actions cannot escape the scope even when a trusted principal has a broad capability grant.
     */
    public static ActionRegistry create(MemoryRetrievalService retrieval, WorkingMemoryService working,
                                        EpisodicMemoryService episodic, SemanticMemoryService semantic,
                                        ProceduralMemoryService procedural, MemoryScopeId boundScope,
                                        ReasoningProvider reasoning, SupplyChainPerceptionPort perception,
                                        SupplyChainSimulationWorld world) {
        Objects.requireNonNull(semantic, "semantic");
        Objects.requireNonNull(procedural, "procedural");
        Objects.requireNonNull(boundScope, "boundScope");
        return createCatalog(retrieval, working, episodic, semantic, procedural, boundScope,
                reasoning, perception, world);
    }

    private static ActionRegistry createCatalog(MemoryRetrievalService retrieval, WorkingMemoryService working,
                                                 EpisodicMemoryService episodic, SemanticMemoryService semantic,
                                                 ProceduralMemoryService procedural, MemoryScopeId boundScope,
                                                 ReasoningProvider reasoning, SupplyChainPerceptionPort perception,
                                                 SupplyChainSimulationWorld world) {
        Objects.requireNonNull(retrieval, "retrieval");
        Objects.requireNonNull(working, "working");
        Objects.requireNonNull(episodic, "episodic");
        Objects.requireNonNull(reasoning, "reasoning");
        Objects.requireNonNull(perception, "perception");
        Objects.requireNonNull(world, "world");
        var registry = new ActionRegistry();
        registry.register(new MemoryRetrievalAction(retrieval));
        registry.register(new WorkingMemoryUpdateAction(working));
        if (semantic == null && procedural == null) {
            registry.register(new EpisodeLearningAction(episodic));
        } else {
            if (semantic == null || procedural == null || boundScope == null) {
                throw new IllegalArgumentException("semantic, procedural and bound-scope configuration must be supplied together");
            }
            LearningActionCatalog.register(registry, episodic, semantic, procedural, boundScope);
        }
        registry.register(new ReasoningAction(reasoning));
        registry.register(new SupplyChainPerceptionActions.ReadSupplier(perception));
        registry.register(new SupplyChainPerceptionActions.ReadInventoryLot(perception));
        registry.register(new SupplyChainPerceptionActions.ReadOrder(perception));
        registry.register(new SupplyChainPerceptionActions.ReadShipment(perception));
        registry.register(new SupplyChainPerceptionActions.ReadAvailability(perception));
        registry.register(new SupplyChainPerceptionActions.ReadQualityDisposition(perception));
        registry.register(new SupplyChainGroundingActions.ReserveInventory(world));
        registry.register(new SupplyChainGroundingActions.ReleaseReservation(world));
        registry.register(new SupplyChainGroundingActions.PlacePurchaseOrder(world));
        registry.register(new SupplyChainGroundingActions.RerouteShipment(world));
        return registry;
    }
}
