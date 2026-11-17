package dev.supplychain.agent.actions.api;

/** Small, explicit capability vocabulary. Capabilities are issued by trusted application code. */
public enum Capability {
    MEMORY_RETRIEVE,
    MEMORY_UPDATE_WORKING,
    MEMORY_RECORD_EPISODE,
    REASONING_INVOKE,
    SUPPLIER_READ,
    INVENTORY_READ,
    ORDER_READ,
    SHIPMENT_READ,
    QUALITY_READ,
    SHIPMENT_REROUTE,
    INVENTORY_RESERVE,
    INVENTORY_RELEASE,
    PURCHASE_ORDER_PLACE
}
