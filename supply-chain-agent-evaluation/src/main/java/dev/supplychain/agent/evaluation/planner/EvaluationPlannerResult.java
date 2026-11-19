package dev.supplychain.agent.evaluation.planner;

import dev.supplychain.agent.planning.api.PlanCandidate;
import dev.supplychain.agent.planning.api.PlannerTerminationReason;
import dev.supplychain.agent.planning.api.PlanningStatistics;
import dev.supplychain.agent.planning.api.PlannerTraceNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record EvaluationPlannerResult(
        String plannerId,
        String selectedPlannerId,
        List<PlanCandidate> candidates,
        PlannerTerminationReason terminationReason,
        PlanningStatistics statistics,
        Map<String, Long> resourceUsage,
        String explanation,
        List<PlannerTraceNode> trace) {
    public EvaluationPlannerResult {
        plannerId = required(plannerId, "plannerId");
        selectedPlannerId = selectedPlannerId == null || selectedPlannerId.isBlank() ? null : selectedPlannerId.trim();
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        terminationReason = Objects.requireNonNull(terminationReason, "terminationReason");
        statistics = Objects.requireNonNull(statistics, "statistics");
        resourceUsage = Map.copyOf(Objects.requireNonNull(resourceUsage, "resourceUsage"));
        if (resourceUsage.values().stream().anyMatch(value -> value == null || value < 0)) {
            throw new IllegalArgumentException("resource usage values must be nonnegative");
        }
        explanation = required(explanation, "explanation");
        trace = List.copyOf(Objects.requireNonNull(trace, "trace"));
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return normalized;
    }
}
