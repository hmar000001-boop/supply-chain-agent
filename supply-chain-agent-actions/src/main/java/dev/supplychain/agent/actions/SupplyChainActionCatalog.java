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
import dev.supplychain.agent.memory.episodic.EpisodicMemoryService;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalService;
import dev.supplychain.agent.memory.working.WorkingMemoryService;
import java.util.Objects;

/** Composition helper for the complete M3 action catalog; no framework types cross this boundary. */
public final class SupplyChainActionCatalog {
    private SupplyChainActionCatalog() { }

    public static ActionRegistry create(MemoryRetrievalService retrieval, WorkingMemoryService working,
                                        EpisodicMemoryService episodic, ReasoningProvider reasoning,
                                        SupplyChainPerceptionPort perception, SupplyChainSimulationWorld world) {
        Objects.requireNonNull(retrieval, "retrieval");
        Objects.requireNonNull(working, "working");
        Objects.requireNonNull(episodic, "episodic");
        Objects.requireNonNull(reasoning, "reasoning");
        Objects.requireNonNull(perception, "perception");
        Objects.requireNonNull(world, "world");
        var registry = new ActionRegistry();
        registry.register(new MemoryRetrievalAction(retrieval));
        registry.register(new WorkingMemoryUpdateAction(working));
        registry.register(new EpisodeLearningAction(episodic));
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
