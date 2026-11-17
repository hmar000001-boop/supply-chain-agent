package dev.supplychain.agent.actions.external.perception;

import dev.supplychain.agent.domain.inventory.InventoryLotId;
import java.util.Objects;

public record ReadAvailableInventoryRequest(InventoryLotId inventoryLotId) {
    public ReadAvailableInventoryRequest { inventoryLotId = Objects.requireNonNull(inventoryLotId, "inventoryLotId"); }
}
