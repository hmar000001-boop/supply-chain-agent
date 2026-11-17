package dev.supplychain.agent.simulator;

import dev.supplychain.agent.domain.world.SupplyChainSnapshot;
import dev.supplychain.agent.simulator.effect.SimulationEffect;
import java.util.List;
import java.util.Objects;

/** Result of a pure deterministic transition, including the new immutable world snapshot. */
public record SimulationResult(String commandId, long previousVersion, long newVersion,
                               SupplyChainSnapshot snapshot, List<SimulationEffect> effects) {
    public SimulationResult {
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(snapshot, "snapshot");
        effects = List.copyOf(Objects.requireNonNull(effects, "effects"));
        if (newVersion != previousVersion + 1 || snapshot.version() != newVersion) {
            throw new IllegalArgumentException("A successful transition must advance snapshot version exactly once");
        }
    }
}
