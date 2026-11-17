package dev.supplychain.agent.actions.external.grounding;

import dev.supplychain.agent.domain.procurement.PurchaseOrder;
import dev.supplychain.agent.domain.procurement.PurchaseOrderLine;
import java.util.Objects;

public record PlacePurchaseOrderRequest(long expectedWorldVersion, PurchaseOrder purchaseOrder) {
    public PlacePurchaseOrderRequest {
        if (expectedWorldVersion < 0) throw new IllegalArgumentException("expectedWorldVersion cannot be negative");
        purchaseOrder = Objects.requireNonNull(purchaseOrder, "purchaseOrder");
    }
    public String canonical() {
        String lines = purchaseOrder.lines().stream()
                .sorted(java.util.Comparator.comparing((PurchaseOrderLine line) -> line.productId().value())
                        .thenComparing(line -> line.orderedQuantity().toString())
                        .thenComparing(line -> line.receivedQuantity().toString())
                        .thenComparing(line -> line.unitPrice().toString()))
                .map(line -> line.productId() + "=" + line.orderedQuantity() + "/received="
                        + line.receivedQuantity() + "@" + line.unitPrice())
                .collect(java.util.stream.Collectors.joining(","));
        return expectedWorldVersion + "|" + purchaseOrder.id() + "|" + purchaseOrder.supplierId() + "|" + lines
                + "|" + purchaseOrder.createdAt() + "|" + purchaseOrder.expectedDeliveryAt()
                + "|" + purchaseOrder.status() + "|v" + purchaseOrder.version();
    }
}
