package dev.supplychain.agent.planning.api;

import java.util.List;
import java.util.Objects;

public record PlanningResult(PlannerId plannerId, List<PlanCandidate> candidates,
                             PlannerTerminationReason terminationReason,
                             PlanningStatistics statistics, String sessionId,
                             String selectionExplanation, List<PlannerTraceNode> trace) {
    public PlanningResult {
        plannerId = Objects.requireNonNull(plannerId, "plannerId");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        terminationReason = Objects.requireNonNull(terminationReason, "terminationReason");
        statistics = Objects.requireNonNull(statistics, "statistics");
        sessionId = require(sessionId, "sessionId");
        selectionExplanation = require(selectionExplanation, "selectionExplanation");
        trace = List.copyOf(Objects.requireNonNull(trace, "trace"));
    }
    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
