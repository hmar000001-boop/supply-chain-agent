package dev.supplychain.agent.domain.recovery;

import dev.supplychain.agent.domain.catalog.ProductId;
import dev.supplychain.agent.domain.common.Quantity;
import dev.supplychain.agent.domain.inventory.InventoryLotId;
import dev.supplychain.agent.domain.logistics.RouteId;
import dev.supplychain.agent.domain.logistics.ShipmentId;
import dev.supplychain.agent.domain.orders.CustomerOrderId;
import dev.supplychain.agent.domain.orders.OrderLineId;
import dev.supplychain.agent.domain.procurement.PurchaseOrderId;
import dev.supplychain.agent.domain.supplier.SupplierId;
import java.util.Objects;

/** Typed proposed recovery operations. These are plans, not executable authorization grants. */
public sealed interface RecoveryAction permits RecoveryAction.ReserveInventory,
        RecoveryAction.PlacePurchaseOrder, RecoveryAction.RerouteShipment,
        RecoveryAction.RequestSupplierExpedite {

    record ReserveInventory(CustomerOrderId orderId, OrderLineId orderLineId,
                            InventoryLotId lotId, Quantity fulfillmentQuantity) implements RecoveryAction {
        public ReserveInventory {
            Objects.requireNonNull(orderId); Objects.requireNonNull(orderLineId);
            Objects.requireNonNull(lotId); Objects.requireNonNull(fulfillmentQuantity);
            if (!fulfillmentQuantity.isPositive()) throw new IllegalArgumentException("fulfillmentQuantity must be positive");
        }
    }

    record PlacePurchaseOrder(PurchaseOrderId purchaseOrderId) implements RecoveryAction {
        public PlacePurchaseOrder { Objects.requireNonNull(purchaseOrderId); }
    }

    record RerouteShipment(ShipmentId shipmentId, RouteId alternativeRouteId) implements RecoveryAction {
        public RerouteShipment { Objects.requireNonNull(shipmentId); Objects.requireNonNull(alternativeRouteId); }
    }

    record RequestSupplierExpedite(SupplierId supplierId, ProductId productId,
                                   Quantity requestedQuantity) implements RecoveryAction {
        public RequestSupplierExpedite {
            Objects.requireNonNull(supplierId); Objects.requireNonNull(productId); Objects.requireNonNull(requestedQuantity);
            if (!requestedQuantity.isPositive()) throw new IllegalArgumentException("requestedQuantity must be positive");
        }
    }
}
