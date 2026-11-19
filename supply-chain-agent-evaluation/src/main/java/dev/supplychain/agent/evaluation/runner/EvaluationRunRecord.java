package dev.supplychain.agent.evaluation.runner;

import dev.supplychain.agent.evaluation.catalog.ScenarioDefinition;
import dev.supplychain.agent.evaluation.fixture.ScenarioFixture;
import dev.supplychain.agent.evaluation.planner.EvaluationPlannerResult;
import dev.supplychain.agent.planning.api.PlanCandidate;
import dev.supplychain.agent.planning.api.PlannerTraceNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Raw, per-scenario/per-procedure result row. Planning runs never authorize or execute actions. */
public record EvaluationRunRecord(
        String scenarioId,
        String fixtureVersion,
        long seed,
        String budgetProfile,
        String scenarioKind,
        String fixtureProfile,
        int scenarioOrderIndex,
        String fixtureFingerprint,
        String requestFingerprint,
        String plannerId,
        String selectedPlannerId,
        int executionOrderIndex,
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
        boolean externalDispatchAttempted,
        List<CandidateOutcome> candidateOutcomes,
        List<TraceOutcome> trace) {

    public EvaluationRunRecord {
        scenarioId = required(scenarioId, "scenarioId");
        fixtureVersion = required(fixtureVersion, "fixtureVersion");
        budgetProfile = required(budgetProfile, "budgetProfile");
        scenarioKind = required(scenarioKind, "scenarioKind");
        fixtureProfile = required(fixtureProfile, "fixtureProfile");
        fixtureFingerprint = required(fixtureFingerprint, "fixtureFingerprint");
        requestFingerprint = required(requestFingerprint, "requestFingerprint");
        plannerId = required(plannerId, "plannerId");
        selectedPlannerId = selectedPlannerId == null || selectedPlannerId.isBlank() ? null : selectedPlannerId.trim();
        terminationReason = required(terminationReason, "terminationReason");
        expectedEffectBoundary = required(expectedEffectBoundary, "expectedEffectBoundary");
        resourceUsage = Map.copyOf(Objects.requireNonNull(resourceUsage, "resourceUsage"));
        candidateOutcomes = List.copyOf(Objects.requireNonNull(candidateOutcomes, "candidateOutcomes"));
        trace = List.copyOf(Objects.requireNonNull(trace, "trace"));
        if (seed < 0 || scenarioOrderIndex < 0 || executionOrderIndex < 0 || candidateCount < 0 || feasibleCandidateCount < 0 || hardViolationCount < 0
                || expansions < 0 || evaluations < 0 || modelCalls < 0 || rollouts < 0
                || prunedBranches < 0 || backtracks < 0 || runtimeNanos < 0) {
            throw new IllegalArgumentException("evaluation result counts cannot be negative");
        }
        if (resourceUsage.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getKey().isBlank()
                || entry.getValue() == null || entry.getValue() < 0)) {
            throw new IllegalArgumentException("resource usage requires nonblank units and nonnegative values");
        }
        if (feasibleCandidateCount > candidateCount || candidateOutcomes.size() != candidateCount) {
            throw new IllegalArgumentException("candidate counts do not match candidate outcome details");
        }
        if (bestScore != null && !Double.isFinite(bestScore)) throw new IllegalArgumentException("bestScore must be finite when present");
        if (!plannerOnly) throw new IllegalArgumentException("M8 result rows must be planner-only");
        if (externalDispatchAttempted) throw new IllegalArgumentException("planner-only evaluation must never dispatch external actions");
    }

    public static EvaluationRunRecord from(ScenarioFixture fixture, EvaluationPlannerResult result,
                                           long runtimeNanos, int scenarioOrderIndex, int executionOrderIndex) {
        ScenarioDefinition scenario = fixture.definition();
        PlanCandidate best = result.candidates().stream().filter(c -> c.evaluation().feasible())
                .max(java.util.Comparator.comparingDouble(c -> c.evaluation().score())).orElse(null);
        int feasible = (int) result.candidates().stream().filter(c -> c.evaluation().feasible()).count();
        int violations = result.candidates().stream().mapToInt(c -> c.evaluation().violations().size()).sum();
        var stats = result.statistics();
        List<CandidateOutcome> outcomes = result.candidates().stream().map(CandidateOutcome::from).toList();
        List<TraceOutcome> trace = result.trace().stream().map(TraceOutcome::from).toList();
        return new EvaluationRunRecord(scenario.scenarioId(), scenario.fixtureVersion(), scenario.seed(),
                scenario.budgetProfile(), scenario.kind(), scenario.fixtureProfile(), scenarioOrderIndex, fixture.fixtureFingerprint(), fixture.planningRequest().fingerprint(), result.plannerId(),
                result.selectedPlannerId(), executionOrderIndex, result.terminationReason().name(), result.candidates().size(), feasible,
                best == null ? null : best.candidateId(), best == null ? null : best.evaluation().score(), violations,
                stats.expansions(), stats.evaluations(), stats.modelCalls(), stats.rollouts(),
                stats.prunedBranches(), stats.backtracks(), result.resourceUsage(), runtimeNanos,
                scenario.expectedEffectBoundary().name(), true, false, outcomes, trace);
    }

    public static EvaluationRunRecord from(ScenarioFixture fixture, EvaluationPlannerResult result, long runtimeNanos) {
        return from(fixture, result, runtimeNanos, 0, 0);
    }

    /** Logical signature deliberately excludes wall-clock runtime and comparator-order position. */
    public String logicalFingerprint() {
        String logical = scenarioId + "|" + fixtureVersion + "|" + seed + "|" + budgetProfile + "|" + scenarioKind + "|"
                + fixtureProfile + "|" + fixtureFingerprint + "|" + requestFingerprint + "|" + plannerId + "|" + selectedPlannerId + "|"
                + terminationReason + "|" + candidateCount + "|" + feasibleCandidateCount + "|" + bestCandidateId + "|"
                + bestScore + "|" + hardViolationCount + "|" + expansions + "|" + evaluations + "|" + modelCalls + "|"
                + rollouts + "|" + prunedBranches + "|" + backtracks + "|" + new TreeMap<>(resourceUsage) + "|"
                + candidateOutcomes + "|" + trace;
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(logical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public CandidateOutcome bestFeasibleOutcome() {
        return candidateOutcomes.stream().filter(CandidateOutcome::feasible)
                .max(java.util.Comparator.comparingDouble(CandidateOutcome::score)).orElse(null);
    }

    public String toJsonLine() {
        return "{\"recordType\":\"plannerRun\"," +
                field("scenarioId", scenarioId) + "," + field("fixtureVersion", fixtureVersion) + "," +
                "\"seed\":" + seed + "," + field("budgetProfile", budgetProfile) + "," +
                field("scenarioKind", scenarioKind) + "," + field("fixtureProfile", fixtureProfile) + "," +
                field("fixtureFingerprint", fixtureFingerprint) + "," + field("requestFingerprint", requestFingerprint) + "," +
                field("plannerId", plannerId) + "," + field("selectedPlannerId", selectedPlannerId) + "," +
                "\"scenarioOrderIndex\":" + scenarioOrderIndex + "," + "\"executionOrderIndex\":" + executionOrderIndex + "," + field("terminationReason", terminationReason) + "," +
                "\"candidateCount\":" + candidateCount + ",\"feasibleCandidateCount\":" + feasibleCandidateCount + "," +
                field("bestCandidateId", bestCandidateId) + "," + number("bestScore", bestScore) + "," +
                "\"hardViolationCount\":" + hardViolationCount + ",\"expansions\":" + expansions + "," +
                "\"evaluations\":" + evaluations + ",\"modelCalls\":" + modelCalls + "," +
                "\"rollouts\":" + rollouts + ",\"prunedBranches\":" + prunedBranches + "," +
                "\"backtracks\":" + backtracks + "," + resourceUsageJson() + "," +
                "\"runtimeNanos\":" + runtimeNanos + "," + field("expectedEffectBoundary", expectedEffectBoundary) + "," +
                "\"plannerOnly\":" + plannerOnly + ",\"externalDispatchAttempted\":" + externalDispatchAttempted + "," +
                "\"candidateOutcomes\":[" + candidateOutcomes.stream().map(CandidateOutcome::toJson)
                        .collect(java.util.stream.Collectors.joining(",")) + "]," +
                "\"trace\":[" + trace.stream().map(TraceOutcome::toJson).collect(java.util.stream.Collectors.joining(",")) + "]}";
    }

    private String resourceUsageJson() {
        String entries = new TreeMap<>(resourceUsage).entrySet().stream()
                .map(entry -> quote(entry.getKey()) + ":" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(","));
        return "\"resourceUsage\":{" + entries + "}";
    }

    public record CandidateOutcome(String candidateId, boolean feasible, double score,
                                   double expectedFulfillmentProgress, String estimatedCost,
                                   String currency, long expectedDurationSeconds, double aggregateRisk,
                                   double robustness, String predictedCompletionAt, boolean targetDeadlineBreach,
                                   boolean approvalRequired, List<String> violations) {
        public CandidateOutcome {
            candidateId = required(candidateId, "candidateId");
            estimatedCost = required(estimatedCost, "estimatedCost");
            currency = required(currency, "currency");
            predictedCompletionAt = required(predictedCompletionAt, "predictedCompletionAt");
            violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
            if (!Double.isFinite(score) || !Double.isFinite(expectedFulfillmentProgress)
                    || !Double.isFinite(aggregateRisk) || !Double.isFinite(robustness)
                    || expectedDurationSeconds < 0) throw new IllegalArgumentException("invalid candidate outcome metrics");
        }
        static CandidateOutcome from(PlanCandidate candidate) {
            var evaluation = candidate.evaluation();
            return new CandidateOutcome(candidate.candidateId(), evaluation.feasible(), evaluation.score(),
                    evaluation.expectedFulfillmentProgress(), evaluation.estimatedCost().amount().toPlainString(),
                    evaluation.estimatedCost().currency().getCurrencyCode(), evaluation.expectedDuration().toSeconds(),
                    evaluation.aggregateRisk(), evaluation.robustness(), evaluation.predictedCompletionAt().toString(),
                    evaluation.targetDeadlineBreach(), evaluation.approvalRequired(), evaluation.violations());
        }
        String toJson() {
            return "{\"candidateId\":" + quote(candidateId) + ",\"feasible\":" + feasible + ",\"score\":" + score
                    + ",\"expectedFulfillmentProgress\":" + expectedFulfillmentProgress
                    + ",\"estimatedCost\":" + quote(estimatedCost) + ",\"currency\":" + quote(currency)
                    + ",\"expectedDurationSeconds\":" + expectedDurationSeconds
                    + ",\"aggregateRisk\":" + aggregateRisk + ",\"robustness\":" + robustness
                    + ",\"predictedCompletionAt\":" + quote(predictedCompletionAt)
                    + ",\"targetDeadlineBreach\":" + targetDeadlineBreach + ",\"approvalRequired\":" + approvalRequired
                    + ",\"violations\":[" + violations.stream().map(EvaluationRunRecord::quote)
                            .collect(java.util.stream.Collectors.joining(",")) + "]}";
        }
    }

    public record TraceOutcome(String nodeId, String parentNodeId, String actionId, int depth,
                               double score, long visits, double meanReward,
                               String disposition, List<String> childNodeIds) {
        public TraceOutcome {
            nodeId = required(nodeId, "nodeId");
            disposition = required(disposition, "disposition");
            childNodeIds = List.copyOf(Objects.requireNonNull(childNodeIds, "childNodeIds"));
        }
        static TraceOutcome from(PlannerTraceNode node) {
            return new TraceOutcome(node.nodeId(), node.parentNodeId(), node.actionId(), node.depth(),
                    node.score(), node.visits(), node.meanReward(), node.disposition(), node.childNodeIds());
        }
        String toJson() {
            return "{\"nodeId\":" + quote(nodeId) + ",\"parentNodeId\":" + nullableQuote(parentNodeId)
                    + ",\"actionId\":" + nullableQuote(actionId) + ",\"depth\":" + depth
                    + ",\"score\":" + score + ",\"visits\":" + visits + ",\"meanReward\":" + meanReward
                    + ",\"disposition\":" + quote(disposition) + ",\"childNodeIds\":["
                    + childNodeIds.stream().map(EvaluationRunRecord::quote).collect(java.util.stream.Collectors.joining(",")) + "]}";
        }
    }

    public static String quote(String value) { return "\"" + escape(value) + "\""; }
    public static String nullableQuote(String value) { return value == null ? "null" : quote(value); }
    private static String field(String key, String value) { return quote(key) + ":" + nullableQuote(value); }
    private static String number(String key, Double value) { return quote(key) + ":" + (value == null ? "null" : Double.toString(value)); }
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
