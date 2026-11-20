package dev.supplychain.agent.mechanisms.b6;

/** Durable explanation for a reservation that left the normal ACTIVE lifecycle. */
public enum BudgetReservationRecovery {
    NONE,
    EXPIRED_BEFORE_START,
    EXPIRED_AFTER_START_CONSERVATIVELY_CHARGED,
    EXPLICITLY_RELEASED
}
