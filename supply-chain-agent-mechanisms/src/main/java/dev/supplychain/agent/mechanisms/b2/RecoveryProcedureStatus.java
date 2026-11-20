package dev.supplychain.agent.mechanisms.b2;

/** A draft is never executable. APPROVED records a separate trusted review decision only. */
public enum RecoveryProcedureStatus {
    DRAFT,
    APPROVED,
    REJECTED,
    EXPIRED
}
