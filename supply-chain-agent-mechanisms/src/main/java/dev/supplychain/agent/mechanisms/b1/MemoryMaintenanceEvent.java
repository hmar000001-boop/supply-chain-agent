package dev.supplychain.agent.mechanisms.b1;

import java.time.Instant;
import java.util.Objects;

public record MemoryMaintenanceEvent(String eventId, String candidateId, MaintenanceEventType type,
                                     double costUtilityPoints, Instant occurredAt, String evidenceReference) {
    public MemoryMaintenanceEvent {
        eventId = require(eventId, "eventId");
        candidateId = require(candidateId, "candidateId");
        type = Objects.requireNonNull(type, "type");
        if (!Double.isFinite(costUtilityPoints) || costUtilityPoints < 0.0) throw new IllegalArgumentException("maintenance cost must be finite and nonnegative");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        evidenceReference = evidenceReference == null || evidenceReference.isBlank() ? null : evidenceReference.trim();
    }
    private static String require(String v, String n) { String s=Objects.requireNonNull(v,n).trim(); if(s.isEmpty()) throw new IllegalArgumentException(n+" cannot be blank"); return s; }
}
