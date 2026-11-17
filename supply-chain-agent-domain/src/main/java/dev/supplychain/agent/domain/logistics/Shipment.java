package dev.supplychain.agent.domain.logistics;

import dev.supplychain.agent.domain.orders.CustomerOrderId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record Shipment(
        ShipmentId id,
        CarrierId carrierId,
        RouteId routeId,
        List<ShipmentLine> lines,
        Instant expectedArrivalAt,
        Instant actualArrivalAt,
        ShipmentStatus status,
        long version) {
    public Shipment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(carrierId, "carrierId");
        Objects.requireNonNull(routeId, "routeId");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        if (lines.isEmpty()) throw new IllegalArgumentException("Shipment must contain at least one line");
        Objects.requireNonNull(expectedArrivalAt, "expectedArrivalAt");
        Objects.requireNonNull(status, "status");
        if (status == ShipmentStatus.DELIVERED && actualArrivalAt == null) {
            throw new IllegalArgumentException("Delivered shipment requires actualArrivalAt");
        }
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
    }
    public Shipment delayedUntil(Instant newExpectedArrivalAt) {
        return new Shipment(id, carrierId, routeId, lines, newExpectedArrivalAt, actualArrivalAt,
                ShipmentStatus.DELAYED, version + 1);
    }
    public Set<CustomerOrderId> affectedOrders() {
        return lines.stream().map(ShipmentLine::orderId).collect(Collectors.toUnmodifiableSet());
    }
}
