package dev.supplychain.agent.actions.external.perception;

import dev.supplychain.agent.domain.world.SupplyChainSnapshot;

@FunctionalInterface
public interface SupplyChainSnapshotProvider {
    SupplyChainSnapshot currentSnapshot();
}
