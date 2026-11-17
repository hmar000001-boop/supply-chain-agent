package dev.supplychain.agent.actions.external.grounding;

import dev.supplychain.agent.domain.logistics.RouteId;
import dev.supplychain.agent.domain.logistics.ShipmentId;
import java.time.Instant;
import java.util.Objects;

public record RerouteShipmentRequest(long expectedWorldVersion, ShipmentId shipmentId, RouteId newRouteId,
                                     Instant revisedArrivalAt, String reason) {
    public RerouteShipmentRequest {
        if (expectedWorldVersion < 0) throw new IllegalArgumentException("expectedWorldVersion cannot be negative");
        shipmentId = Objects.requireNonNull(shipmentId, "shipmentId");
        newRouteId = Objects.requireNonNull(newRouteId, "newRouteId");
        revisedArrivalAt = Objects.requireNonNull(revisedArrivalAt, "revisedArrivalAt");
        reason = Objects.requireNonNull(reason, "reason").trim();
        if (reason.isEmpty()) throw new IllegalArgumentException("reason cannot be blank");
    }
    public String canonical() {
        return expectedWorldVersion + "|" + shipmentId + "|" + newRouteId + "|" + revisedArrivalAt + "|" + reason;
    }
}
