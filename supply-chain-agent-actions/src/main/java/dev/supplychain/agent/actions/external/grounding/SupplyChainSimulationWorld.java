package dev.supplychain.agent.actions.external.grounding;

import dev.supplychain.agent.domain.world.SupplyChainSnapshot;
import dev.supplychain.agent.simulator.SimulationResult;
import dev.supplychain.agent.simulator.command.SimulationCommand;

public interface SupplyChainSimulationWorld {
    SupplyChainSnapshot currentSnapshot();
    SimulationResult apply(long expectedVersion, SimulationCommand command);
}
