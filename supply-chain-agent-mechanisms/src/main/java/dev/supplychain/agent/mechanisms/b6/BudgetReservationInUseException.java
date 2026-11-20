package dev.supplychain.agent.mechanisms.b6;

/** A different live owner currently holds the operation's reservation lease. */
public final class BudgetReservationInUseException extends IllegalStateException {
    public BudgetReservationInUseException(String message) { super(message); }
}
