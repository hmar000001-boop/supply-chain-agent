package dev.supplychain.agent.domain.logistics;

import dev.supplychain.agent.domain.catalog.ProductId;
import dev.supplychain.agent.domain.catalog.SubstitutionId;
import dev.supplychain.agent.domain.common.Quantity;
import dev.supplychain.agent.domain.orders.CustomerOrderId;
import dev.supplychain.agent.domain.orders.OrderLineId;
import java.time.Instant;
import java.util.Objects;

/**
 * Physical shipment quantity and customer-order fulfillment quantity are kept separately so
 * an approved substitute can be shipped without corrupting the requested-product accounting.
 */
public record ShipmentLine(
        CustomerOrderId orderId,
        OrderLineId orderLineId,
        ProductId productId,
        SubstitutionId substitutionId,
        Quantity shippedQuantity,
        Quantity fulfillmentQuantity,
        Instant allocatedAt) {
    public ShipmentLine {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(orderLineId, "orderLineId");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(shippedQuantity, "shippedQuantity");
        Objects.requireNonNull(fulfillmentQuantity, "fulfillmentQuantity");
        Objects.requireNonNull(allocatedAt, "allocatedAt");
        if (!shippedQuantity.isPositive() || !fulfillmentQuantity.isPositive()) {
            throw new IllegalArgumentException("Shipment quantities must be positive");
        }
    }
}
