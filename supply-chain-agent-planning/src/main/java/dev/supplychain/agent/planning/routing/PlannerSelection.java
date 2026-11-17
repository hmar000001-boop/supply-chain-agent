package dev.supplychain.agent.planning.routing;

import dev.supplychain.agent.planning.api.PlannerId;
import java.util.Objects;

public record PlannerSelection(PlannerId plannerId, String explanation) {
    public PlannerSelection {
        plannerId = Objects.requireNonNull(plannerId, "plannerId");
        explanation = Objects.requireNonNull(explanation, "explanation").trim();
        if (explanation.isEmpty()) throw new IllegalArgumentException("explanation cannot be blank");
    }
}
