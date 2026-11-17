package dev.supplychain.agent.actions.external.perception;

import dev.supplychain.agent.domain.logistics.ShipmentId;
import java.util.Objects;

public record ReadShipmentRequest(ShipmentId shipmentId) {
    public ReadShipmentRequest { shipmentId = Objects.requireNonNull(shipmentId, "shipmentId"); }
}
