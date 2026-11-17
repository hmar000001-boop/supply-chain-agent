package dev.supplychain.agent.domain.inventory;

import dev.supplychain.agent.domain.catalog.ProductId;
import dev.supplychain.agent.domain.catalog.SubstitutionId;
import dev.supplychain.agent.domain.common.Quantity;
import dev.supplychain.agent.domain.orders.CustomerOrderId;
import dev.supplychain.agent.domain.orders.OrderLineId;
import java.time.Instant;
import java.util.Objects;

/** Reservation stores both physical stock reserved and required-order quantity it covers. */
public record InventoryReservation(
        ReservationId id,
        CustomerOrderId orderId,
        OrderLineId orderLineId,
        InventoryLotId lotId,
        ProductId reservedProductId,
        SubstitutionId substitutionId,
        Quantity reservedQuantity,
        Quantity fulfillmentQuantity,
        ReservationStatus status,
        Instant createdAt) {
    public InventoryReservation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(orderLineId, "orderLineId");
        Objects.requireNonNull(lotId, "lotId");
        Objects.requireNonNull(reservedProductId, "reservedProductId");
        Objects.requireNonNull(reservedQuantity, "reservedQuantity");
        Objects.requireNonNull(fulfillmentQuantity, "fulfillmentQuantity");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (!reservedQuantity.isPositive() || !fulfillmentQuantity.isPositive()) {
            throw new IllegalArgumentException("Reservation quantities must be positive");
        }
    }
    public InventoryReservation release() {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Only an active reservation can be released");
        }
        return new InventoryReservation(id, orderId, orderLineId, lotId, reservedProductId, substitutionId,
                reservedQuantity, fulfillmentQuantity, ReservationStatus.RELEASED, createdAt);
    }
}
