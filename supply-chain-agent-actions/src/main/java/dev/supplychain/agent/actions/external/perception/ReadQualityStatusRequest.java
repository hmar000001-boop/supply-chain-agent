package dev.supplychain.agent.actions.external.perception;

import dev.supplychain.agent.domain.inventory.InventoryLotId;
import java.util.Objects;

public record ReadQualityStatusRequest(InventoryLotId inventoryLotId) {
    public ReadQualityStatusRequest { inventoryLotId = Objects.requireNonNull(inventoryLotId, "inventoryLotId"); }
}
