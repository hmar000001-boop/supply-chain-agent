package dev.supplychain.agent.domain.disruption;

public enum DisruptionType {
    SUPPLIER_CAPACITY_REDUCTION,
    SUPPLIER_OUTAGE,
    INVENTORY_SHORTAGE,
    INVENTORY_CONFLICT,
    QUALITY_HOLD,
    SHIPMENT_DELAY,
    DEMAND_CHANGE,
    COMPOSITE
}
