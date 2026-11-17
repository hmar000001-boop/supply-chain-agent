package dev.supplychain.agent.domain.inventory;

import dev.supplychain.agent.domain.catalog.ProductId;
import dev.supplychain.agent.domain.common.Quantity;
import java.time.Instant;
import java.util.Objects;

/** Inventory lot. Reserved quantities are derived from reservation records in the snapshot. */
public record InventoryLot(
        InventoryLotId id,
        ProductId productId,
        InventoryLocationId locationId,
        Quantity quantityOnHand,
        LotQualityStatus qualityStatus,
        Instant expiresAt,
        Instant qualityHeldAt,
        String qualityHoldReason,
        long version) {
    public InventoryLot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(locationId, "locationId");
        Objects.requireNonNull(quantityOnHand, "quantityOnHand");
        Objects.requireNonNull(qualityStatus, "qualityStatus");
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
        if (qualityStatus == LotQualityStatus.QUALITY_HOLD) {
            Objects.requireNonNull(qualityHeldAt, "qualityHeldAt");
            if (qualityHoldReason == null || qualityHoldReason.isBlank()) {
                throw new IllegalArgumentException("A quality hold requires a reason");
            }
        }
    }
    public InventoryLot applyQualityHold(String reason, Instant at) {
        return new InventoryLot(id, productId, locationId, quantityOnHand, LotQualityStatus.QUALITY_HOLD,
                expiresAt, at, reason, version + 1);
    }
    public Quantity availableQuantity(Quantity currentlyReserved, Instant at) {
        Objects.requireNonNull(currentlyReserved, "currentlyReserved");
        Objects.requireNonNull(at, "at");
        if (currentlyReserved.unit() != quantityOnHand.unit()) {
            throw new IllegalArgumentException("Reserved quantity unit does not match lot");
        }
        if (qualityStatus != LotQualityStatus.AVAILABLE || (expiresAt != null && !expiresAt.isAfter(at))) {
            return Quantity.zero(quantityOnHand.unit());
        }
        return quantityOnHand.minus(currentlyReserved);
    }
}
