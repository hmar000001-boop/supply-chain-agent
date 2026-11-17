package dev.supplychain.agent.domain.orders;

import dev.supplychain.agent.domain.catalog.ProductId;
import dev.supplychain.agent.domain.common.Quantity;
import java.util.Objects;

public record OrderLine(
        OrderLineId id,
        ProductId productId,
        Quantity requestedQuantity,
        Quantity fulfilledQuantity) {
    public OrderLine {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(requestedQuantity, "requestedQuantity");
        Objects.requireNonNull(fulfilledQuantity, "fulfilledQuantity");
        if (!requestedQuantity.isPositive()) throw new IllegalArgumentException("requestedQuantity must be positive");
        if (requestedQuantity.unit() != fulfilledQuantity.unit()) {
            throw new IllegalArgumentException("Fulfilled and requested quantity units must match");
        }
        if (fulfilledQuantity.compareTo(requestedQuantity) > 0) {
            throw new IllegalArgumentException("Fulfilled quantity cannot exceed requested quantity");
        }
    }
    public OrderLine withRequestedQuantity(Quantity quantity) {
        return new OrderLine(id, productId, quantity, fulfilledQuantity);
    }
    public OrderLine withFulfilledQuantity(Quantity quantity) {
        return new OrderLine(id, productId, requestedQuantity, quantity);
    }
    public Quantity unfulfilledQuantity() {
        return requestedQuantity.minus(fulfilledQuantity);
    }
}
