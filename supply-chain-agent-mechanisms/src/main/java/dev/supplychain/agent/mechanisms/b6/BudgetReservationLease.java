package dev.supplychain.agent.mechanisms.b6;

import java.time.Instant;
import java.util.Objects;

/**
 * Fenced ownership view of a durable B6 reservation. A lease is not permission to repeat work:
 * operationStartedAt is written before invoking the provider and remains set for that reservation.
 */
public record BudgetReservationLease(
        String reservationId,
        String executionId,
        ResourceUsage reserved,
        Instant createdAt,
        String ownerId,
        long fencingToken,
        Instant leaseExpiresAt,
        Instant operationStartedAt,
        ReservationStatus status,
        ResourceUsage actualUsage,
        BudgetReservationRecovery recovery) {

    public BudgetReservationLease {
        reservationId = required(reservationId, "reservationId");
        executionId = required(executionId, "executionId");
        reserved = Objects.requireNonNull(reserved, "reserved");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        ownerId = ownerId == null ? "" : ownerId.trim();
        if (fencingToken < 0) throw new IllegalArgumentException("fencingToken cannot be negative");
        if ((ownerId.isBlank()) != (leaseExpiresAt == null)) {
            throw new IllegalArgumentException("owner and lease expiry must either both be present or both be absent");
        }
        if (!ownerId.isBlank() && fencingToken < 1) {
            throw new IllegalArgumentException("owned reservations require a positive fencing token");
        }
        if (operationStartedAt != null && ownerId.isBlank()) {
            throw new IllegalArgumentException("only an owned reservation can have an operation-start marker");
        }
        status = Objects.requireNonNull(status, "status");
        recovery = Objects.requireNonNull(recovery, "recovery");
        if (status == ReservationStatus.ACTIVE && recovery != BudgetReservationRecovery.NONE) {
            throw new IllegalArgumentException("active reservations cannot carry a terminal recovery outcome");
        }
        if ((status == ReservationStatus.COMMITTED) != (actualUsage != null)) {
            throw new IllegalArgumentException("actual usage is required exactly for committed reservations");
        }
        if (recovery == BudgetReservationRecovery.EXPIRED_AFTER_START_CONSERVATIVELY_CHARGED
                && (status != ReservationStatus.COMMITTED || operationStartedAt == null || ownerId.isBlank())) {
            throw new IllegalArgumentException("conservative expiry recovery requires a committed, owned, started reservation");
        }
        if (recovery == BudgetReservationRecovery.EXPIRED_BEFORE_START
                && (status != ReservationStatus.RELEASED || operationStartedAt != null || ownerId.isBlank())) {
            throw new IllegalArgumentException("pre-start expiry recovery requires a released, owned, unstarted reservation");
        }
        if (recovery == BudgetReservationRecovery.EXPLICITLY_RELEASED
                && (status != ReservationStatus.RELEASED || operationStartedAt != null)) {
            throw new IllegalArgumentException("explicit release requires a released reservation that never started");
        }
    }

    public boolean operationStarted() { return operationStartedAt != null; }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
