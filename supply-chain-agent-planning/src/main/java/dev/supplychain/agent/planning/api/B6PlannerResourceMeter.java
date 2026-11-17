package dev.supplychain.agent.planning.api;

import dev.supplychain.agent.mechanisms.b6.ResourceBudgetLedger;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Bridges planner charges to the M4 B6 ledger; a durable ledger replaces the in-memory adapter in M7. */
public final class B6PlannerResourceMeter implements PlannerResourceMeter {
    private final ResourceBudgetLedger ledger;
    private final AtomicLong sequence = new AtomicLong();

    public B6PlannerResourceMeter(ResourceBudgetLedger ledger) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    @Override
    public void record(String executionId, PlannerId plannerId, ResourceUsage usage, Instant at) {
        Objects.requireNonNull(plannerId, "plannerId");
        String reservationId = executionId + ":" + plannerId + ":planner-charge:" + sequence.incrementAndGet() + ":" + java.util.UUID.randomUUID();
        ledger.reserve(executionId, reservationId, usage, at);
        ledger.commit(executionId, reservationId, usage, at);
    }
}
