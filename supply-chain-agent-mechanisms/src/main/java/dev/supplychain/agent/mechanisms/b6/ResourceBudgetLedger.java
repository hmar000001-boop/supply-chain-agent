package dev.supplychain.agent.mechanisms.b6;

import java.time.Instant;

public interface ResourceBudgetLedger{
    void register(String executionId,ResourceBudget budget);

    /**
     * Ensures an execution account exists and is bound to exactly the supplied immutable budget.
     * Concurrent hosts may call this during startup; a pre-existing different budget is a security
     * conflict and must never be silently adopted.
     */
    default void ensureRegistered(String executionId, ResourceBudget budget) {
        try {
            register(executionId, budget);
        } catch (IllegalStateException alreadyRegistered) {
            ResourceBudgetSnapshot existing = snapshot(executionId, Instant.EPOCH);
            if (!existing.budget().equals(budget)) {
                throw new SecurityException("execution is already bound to a different resource budget", alreadyRegistered);
            }
        }
    }
    BudgetReservation reserve(String executionId,String reservationId,ResourceUsage requested,Instant at);
    ResourceBudgetSnapshot commit(String executionId,String reservationId,ResourceUsage actual,Instant at);
    ResourceBudgetSnapshot release(String executionId,String reservationId,Instant at);
    ResourceBudgetSnapshot snapshot(String executionId,Instant at);
}
