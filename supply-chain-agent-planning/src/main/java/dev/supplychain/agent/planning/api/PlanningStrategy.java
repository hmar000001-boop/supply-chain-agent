package dev.supplychain.agent.planning.api;

public interface PlanningStrategy {
    PlannerId id();
    PlanningResult plan(PlanningRequest request);
}
