package dev.supplychain.agent.actions.external.grounding;

import dev.supplychain.agent.domain.inventory.ReservationId;
import java.util.Objects;

public record ReleaseReservationRequest(long expectedWorldVersion, ReservationId reservationId) {
    public ReleaseReservationRequest {
        if (expectedWorldVersion < 0) throw new IllegalArgumentException("expectedWorldVersion cannot be negative");
        reservationId = Objects.requireNonNull(reservationId, "reservationId");
    }
    public String canonical() { return expectedWorldVersion + "|" + reservationId; }
}
