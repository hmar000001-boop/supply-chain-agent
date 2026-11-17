package dev.supplychain.agent.domain.procurement;

import dev.supplychain.agent.domain.supplier.SupplierId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record PurchaseOrder(
        PurchaseOrderId id,
        SupplierId supplierId,
        Instant createdAt,
        Instant expectedDeliveryAt,
        PurchaseOrderStatus status,
        List<PurchaseOrderLine> lines,
        long version) {
    public PurchaseOrder {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(supplierId, "supplierId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expectedDeliveryAt, "expectedDeliveryAt");
        Objects.requireNonNull(status, "status");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        if (lines.isEmpty()) throw new IllegalArgumentException("Purchase order must have at least one line");
        if (expectedDeliveryAt.isBefore(createdAt)) throw new IllegalArgumentException("Expected delivery precedes creation");
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
        boolean anyReceived = lines.stream().anyMatch(line -> line.receivedQuantity().isPositive());
        boolean anyOpen = lines.stream().anyMatch(line -> line.openQuantity().isPositive());
        if (status == PurchaseOrderStatus.DRAFT && anyReceived) {
            throw new IllegalArgumentException("A draft purchase order cannot contain received quantities");
        }
        if (status == PurchaseOrderStatus.RECEIVED && anyOpen) {
            throw new IllegalArgumentException("A received purchase order must have all lines fully received");
        }
        if (status == PurchaseOrderStatus.PARTIALLY_RECEIVED && (!anyReceived || !anyOpen)) {
            throw new IllegalArgumentException("A partially received purchase order must contain received and open quantity");
        }
    }
    public PurchaseOrder withStatus(PurchaseOrderStatus changedStatus) {
        return new PurchaseOrder(id, supplierId, createdAt, expectedDeliveryAt, changedStatus, lines, version + 1);
    }
}
