package dev.supplychain.agent.planning.api;

import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import java.time.Instant;

@FunctionalInterface
public interface PlannerResourceMeter {
    void record(String executionId, PlannerId plannerId, ResourceUsage usage, Instant at);
}
