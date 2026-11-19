package dev.supplychain.agent.evaluation.planner;

import dev.supplychain.agent.mechanisms.b6.ResourceUnit;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.api.PlannerResourceMeter;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/** Per-run instrumentation only; it is not the authoritative or durable B6 budget ledger. */
public final class RecordingPlannerResourceMeter implements PlannerResourceMeter {
    private final EnumMap<ResourceUnit, Long> totals = new EnumMap<>(ResourceUnit.class);

    @Override
    public synchronized void record(String executionId, PlannerId plannerId, ResourceUsage usage, Instant at) {
        for (ResourceUnit unit : ResourceUnit.values()) {
            long next = Math.addExact(totals.getOrDefault(unit, 0L), usage.amount(unit));
            totals.put(unit, next);
        }
    }

    public synchronized Map<ResourceUnit, Long> snapshot() {
        return Map.copyOf(totals);
    }
}
