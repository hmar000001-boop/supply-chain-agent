package dev.supplychain.agent.actions.external.grounding;

import dev.supplychain.agent.domain.common.DomainViolation;
import dev.supplychain.agent.domain.world.SupplyChainSnapshot;
import dev.supplychain.agent.simulator.SimulationResult;
import dev.supplychain.agent.simulator.SupplyChainSimulator;
import dev.supplychain.agent.simulator.command.SimulationCommand;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** In-memory simulation adapter for tests/local scenarios; not a transactional production repository. */
public final class InMemorySupplyChainSimulationWorld implements SupplyChainSimulationWorld {
    private final AtomicReference<SupplyChainSnapshot> current;
    private final SupplyChainSimulator simulator;
    public InMemorySupplyChainSimulationWorld(SupplyChainSnapshot initial, SupplyChainSimulator simulator) {
        current = new AtomicReference<>(Objects.requireNonNull(initial, "initial"));
        this.simulator = Objects.requireNonNull(simulator, "simulator");
    }
    @Override public SupplyChainSnapshot currentSnapshot() { return current.get(); }
    @Override public SimulationResult apply(long expectedVersion, SimulationCommand command) {
        SupplyChainSnapshot before = current.get();
        if (before.version() != expectedVersion) throw new DomainViolation(DomainViolation.Code.CONFLICT,
                "World snapshot version is " + before.version() + ", expected " + expectedVersion);
        SimulationResult result = simulator.simulate(before, command);
        if (!current.compareAndSet(before, result.snapshot())) throw new DomainViolation(DomainViolation.Code.CONFLICT,
                "World snapshot changed concurrently; reload and reconsider the action");
        return result;
    }
}
