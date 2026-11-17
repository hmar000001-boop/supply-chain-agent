package dev.supplychain.agent.mechanisms.b6;

import java.time.Instant;

public interface ResourceBudgetLedger{
    void register(String executionId,ResourceBudget budget);
    BudgetReservation reserve(String executionId,String reservationId,ResourceUsage requested,Instant at);
    ResourceBudgetSnapshot commit(String executionId,String reservationId,ResourceUsage actual,Instant at);
    ResourceBudgetSnapshot release(String executionId,String reservationId,Instant at);
    ResourceBudgetSnapshot snapshot(String executionId,Instant at);
}
