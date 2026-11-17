package dev.supplychain.agent.domain.procurement;

public enum PurchaseOrderStatus {
    DRAFT, PLACED, CONFIRMED, PARTIALLY_RECEIVED, RECEIVED, CANCELLED;
    public boolean consumesSupplierCapacity() {
        return this == PLACED || this == CONFIRMED || this == PARTIALLY_RECEIVED;
    }
}
