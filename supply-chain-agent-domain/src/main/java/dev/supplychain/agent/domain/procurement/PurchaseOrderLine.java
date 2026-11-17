package dev.supplychain.agent.domain.procurement;

import dev.supplychain.agent.domain.catalog.ProductId;
import dev.supplychain.agent.domain.common.Money;
import dev.supplychain.agent.domain.common.Quantity;
import java.util.Objects;

public record PurchaseOrderLine(ProductId productId, Quantity orderedQuantity,
                                Quantity receivedQuantity, Money unitPrice) {
    public PurchaseOrderLine {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(orderedQuantity, "orderedQuantity");
        Objects.requireNonNull(receivedQuantity, "receivedQuantity");
        Objects.requireNonNull(unitPrice, "unitPrice");
        if (!orderedQuantity.isPositive()) throw new IllegalArgumentException("orderedQuantity must be positive");
        if (orderedQuantity.unit() != receivedQuantity.unit()) throw new IllegalArgumentException("Quantity units must match");
        if (receivedQuantity.compareTo(orderedQuantity) > 0) throw new IllegalArgumentException("Received exceeds ordered");
    }
    public Quantity openQuantity() { return orderedQuantity.minus(receivedQuantity); }
    public Money extendedPrice() { return unitPrice.multipliedBy(orderedQuantity.value()); }
}
