package dev.supplychain.agent.actions.external.grounding;

import dev.supplychain.agent.domain.common.Quantity;
import dev.supplychain.agent.domain.inventory.InventoryLotId;
import dev.supplychain.agent.domain.inventory.ReservationId;
import dev.supplychain.agent.domain.orders.CustomerOrderId;
import dev.supplychain.agent.domain.orders.OrderLineId;
import java.util.Objects;

public record ReserveInventoryRequest(long expectedWorldVersion, ReservationId reservationId,
                                      CustomerOrderId orderId, OrderLineId orderLineId,
                                      InventoryLotId lotId, Quantity fulfillmentQuantity) {
    public ReserveInventoryRequest {
        if (expectedWorldVersion < 0) throw new IllegalArgumentException("expectedWorldVersion cannot be negative");
        reservationId = Objects.requireNonNull(reservationId, "reservationId");
        orderId = Objects.requireNonNull(orderId, "orderId");
        orderLineId = Objects.requireNonNull(orderLineId, "orderLineId");
        lotId = Objects.requireNonNull(lotId, "lotId");
        fulfillmentQuantity = Objects.requireNonNull(fulfillmentQuantity, "fulfillmentQuantity");
        if (!fulfillmentQuantity.isPositive()) throw new IllegalArgumentException("fulfillmentQuantity must be positive");
    }
    public String canonical() { return expectedWorldVersion + "|" + reservationId + "|" + orderId + "|" + orderLineId + "|" + lotId + "|" + fulfillmentQuantity; }
}
