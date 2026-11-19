package dev.supplychain.agent.evaluation.runner;

import dev.supplychain.agent.evaluation.catalog.ScenarioDefinition;
import dev.supplychain.agent.evaluation.fixture.ScenarioFixture;
import dev.supplychain.agent.evaluation.planner.EvaluationPlannerResult;
import dev.supplychain.agent.planning.api.PlanCandidate;

import java.util.Map;
import java.util.Objects;

/** Raw, per-scenario/per-procedure result row. Safety facts are stated only for the planning-only boundary. */
public record EvaluationRunRecord(
        String scenarioId,
        String fixtureVersion,
        long seed,
        String budgetProfile,
        String fixtureFingerprint,
        String requestFingerprint,
        String plannerId,
        String selectedPlannerId,
        String terminationReason,
        int candidateCount,
        int feasibleCandidateCount,
        String bestCandidateId,
        Double bestScore,
        int hardViolationCount,
        long expansions,
        long evaluations,
        long modelCalls,
        long rollouts,
        long prunedBranches,
        long backtracks,
        Map<String, Long> resourceUsage,
        long runtimeNanos,
        String expectedEffectBoundary,
        boolean plannerOnly,
        boolean externalDispatchAttempted) {

    public EvaluationRunRecord {
        scenarioId = required(scenarioId, "scenarioId");
        fixtureVersion = required(fixtureVersion, "fixtureVersion");
        budgetProfile = required(budgetProfile, "budgetProfile");
        fixtureFingerprint = required(fixtureFingerprint, "fixtureFingerprint");
        requestFingerprint = required(requestFingerprint, "requestFingerprint");
        plannerId = required(plannerId, "plannerId");
        terminationReason = required(terminationReason, "terminationReason");
        expectedEffectBoundary = required(expectedEffectBoundary, "expectedEffectBoundary");
        resourceUsage = Map.copyOf(Objects.requireNonNull(resourceUsage, "resourceUsage"));
        if (resourceUsage.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getKey().isBlank()
                || entry.getValue() == null || entry.getValue() < 0)) {
            throw new IllegalArgumentException("resource usage requires nonblank units and nonnegative values");
        }
        if (seed < 0 || candidateCount < 0 || feasibleCandidateCount < 0 || hardViolationCount < 0
                || expansions < 0 || evaluations < 0 || modelCalls < 0 || rollouts < 0
                || prunedBranches < 0 || backtracks < 0 || runtimeNanos < 0) {
            throw new IllegalArgumentException("evaluation result counts cannot be negative");
        }
        if (feasibleCandidateCount > candidateCount) throw new IllegalArgumentException("feasible candidates cannot exceed candidate count");
        if (bestScore != null && !Double.isFinite(bestScore)) throw new IllegalArgumentException("bestScore must be finite when present");
        if (!plannerOnly) throw new IllegalArgumentException("M8-B1 result rows must be planner-only");
        if (externalDispatchAttempted) throw new IllegalArgumentException("planner-only evaluation must never dispatch external actions");
    }

    public static EvaluationRunRecord from(ScenarioFixture fixture, EvaluationPlannerResult result, long runtimeNanos) {
        ScenarioDefinition scenario = fixture.definition();
        PlanCandidate best = result.candidates().stream().filter(c -> c.evaluation().feasible())
                .max(java.util.Comparator.comparingDouble(c -> c.evaluation().score())).orElse(null);
        int feasible = (int) result.candidates().stream().filter(c -> c.evaluation().feasible()).count();
        int violations = result.candidates().stream().mapToInt(c -> c.evaluation().violations().size()).sum();
        var stats = result.statistics();
        return new EvaluationRunRecord(scenario.scenarioId(), scenario.fixtureVersion(), scenario.seed(),
                scenario.budgetProfile(), fixture.fixtureFingerprint(), fixture.planningRequest().fingerprint(), result.plannerId(),
                result.selectedPlannerId(), result.terminationReason().name(), result.candidates().size(), feasible,
                best == null ? null : best.candidateId(), best == null ? null : best.evaluation().score(), violations,
                stats.expansions(), stats.evaluations(), stats.modelCalls(), stats.rollouts(),
                stats.prunedBranches(), stats.backtracks(), result.resourceUsage(), runtimeNanos,
                scenario.expectedEffectBoundary().name(), true, false);
    }

    public String toJsonLine() {
        return "{" +
                field("scenarioId", scenarioId) + "," + field("fixtureVersion", fixtureVersion) + "," +
                "\"seed\":" + seed + "," + field("budgetProfile", budgetProfile) + "," +
                field("fixtureFingerprint", fixtureFingerprint) + "," +
                field("requestFingerprint", requestFingerprint) + "," + field("plannerId", plannerId) + "," +
                field("selectedPlannerId", selectedPlannerId) + "," + field("terminationReason", terminationReason) + "," +
                "\"candidateCount\":" + candidateCount + "," + "\"feasibleCandidateCount\":" + feasibleCandidateCount + "," +
                field("bestCandidateId", bestCandidateId) + "," + number("bestScore", bestScore) + "," +
                "\"hardViolationCount\":" + hardViolationCount + "," + "\"expansions\":" + expansions + "," +
                "\"evaluations\":" + evaluations + "," + "\"modelCalls\":" + modelCalls + "," +
                "\"rollouts\":" + rollouts + "," + "\"prunedBranches\":" + prunedBranches + "," +
                "\"backtracks\":" + backtracks + "," + resourceUsageJson() + "," +
                "\"runtimeNanos\":" + runtimeNanos + "," +
                field("expectedEffectBoundary", expectedEffectBoundary) + "," +
                "\"plannerOnly\":" + plannerOnly + "," + "\"externalDispatchAttempted\":" + externalDispatchAttempted + "}";
    }

    private String resourceUsageJson() {
        String entries = new java.util.TreeMap<>(resourceUsage).entrySet().stream()
                .map(entry -> "\"" + escape(entry.getKey()) + "\":" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(","));
        return "\"resourceUsage\":{" + entries + "}";
    }

    private static String field(String key, String value) {
        return "\"" + escape(key) + "\":" + (value == null ? "null" : "\"" + escape(value) + "\"");
    }
    private static String number(String key, Double value) {
        return "\"" + key + "\":" + (value == null ? "null" : Double.toString(value));
    }
    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch)); else out.append(ch); }
            }
        }
        return out.toString();
    }
    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
