package dev.supplychain.agent.actions.external.perception;

import dev.supplychain.agent.domain.inventory.InventoryLotId;
import java.util.Objects;

public record ReadInventoryLotRequest(InventoryLotId inventoryLotId) {
    public ReadInventoryLotRequest { inventoryLotId = Objects.requireNonNull(inventoryLotId, "inventoryLotId"); }
}
