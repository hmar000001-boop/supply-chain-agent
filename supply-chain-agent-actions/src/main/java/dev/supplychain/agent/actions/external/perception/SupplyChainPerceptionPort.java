package dev.supplychain.agent.actions.external.perception;

import dev.supplychain.agent.domain.common.Quantity;
import dev.supplychain.agent.domain.inventory.InventoryLot;
import dev.supplychain.agent.domain.inventory.InventoryLotId;
import dev.supplychain.agent.domain.logistics.Shipment;
import dev.supplychain.agent.domain.logistics.ShipmentId;
import dev.supplychain.agent.domain.orders.CustomerOrder;
import dev.supplychain.agent.domain.orders.CustomerOrderId;
import dev.supplychain.agent.domain.supplier.Supplier;
import dev.supplychain.agent.domain.supplier.SupplierId;
import java.time.Instant;

public interface SupplyChainPerceptionPort {
    Observation<Supplier> supplier(SupplierId id, Instant readAt);
    Observation<InventoryLot> inventoryLot(InventoryLotId id, Instant readAt);
    Observation<CustomerOrder> customerOrder(CustomerOrderId id, Instant readAt);
    Observation<Shipment> shipment(ShipmentId id, Instant readAt);
    Observation<Quantity> availableInventory(InventoryLotId id, Instant readAt);
}
