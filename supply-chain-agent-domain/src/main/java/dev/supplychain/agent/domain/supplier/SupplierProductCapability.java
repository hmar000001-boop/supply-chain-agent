package dev.supplychain.agent.domain.supplier;

import dev.supplychain.agent.domain.catalog.ProductId;
import dev.supplychain.agent.domain.common.Quantity;
import java.time.Duration;
import java.util.Objects;

/** Supplier qualification and capacity for one product within the current planning horizon. */
public record SupplierProductCapability(
        ProductId productId,
        Quantity capacity,
        Duration leadTime,
        boolean qualityQualified) {
    public SupplierProductCapability {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(capacity, "capacity");
        Objects.requireNonNull(leadTime, "leadTime");
        if (leadTime.isNegative()) throw new IllegalArgumentException("leadTime cannot be negative");
    }
    public SupplierProductCapability withCapacity(Quantity newCapacity) {
        return new SupplierProductCapability(productId, newCapacity, leadTime, qualityQualified);
    }
}
