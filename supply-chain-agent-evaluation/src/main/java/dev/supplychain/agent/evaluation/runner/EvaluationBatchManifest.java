package dev.supplychain.agent.evaluation.runner;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Reproducibility envelope for a complete deterministic corpus run. */
public record EvaluationBatchManifest(
        String baselineId,
        String corpusVersion,
        String corpusFingerprint,
        String worldModelFingerprint,
        String evaluatorFingerprint,
        String plannerConfigurationFingerprint,
        List<String> plannerIds,
        int scenarioCount,
        int runCount,
        List<String> budgetProfiles,
        String seedPolicy,
        Instant fixtureAsOf,
        String runMode) {
    public EvaluationBatchManifest {
        baselineId = required(baselineId, "baselineId");
        corpusVersion = required(corpusVersion, "corpusVersion");
        corpusFingerprint = required(corpusFingerprint, "corpusFingerprint");
        worldModelFingerprint = required(worldModelFingerprint, "worldModelFingerprint");
        evaluatorFingerprint = required(evaluatorFingerprint, "evaluatorFingerprint");
        plannerConfigurationFingerprint = required(plannerConfigurationFingerprint, "plannerConfigurationFingerprint");
        plannerIds = List.copyOf(Objects.requireNonNull(plannerIds, "plannerIds"));
        budgetProfiles = List.copyOf(Objects.requireNonNull(budgetProfiles, "budgetProfiles"));
        seedPolicy = required(seedPolicy, "seedPolicy");
        if (scenarioCount < 0 || runCount < 0) throw new IllegalArgumentException("manifest counts cannot be negative");
        fixtureAsOf = Objects.requireNonNull(fixtureAsOf, "fixtureAsOf");
        runMode = required(runMode, "runMode");
    }
    public String toJsonLine() {
        String planners = plannerIds.stream().map(EvaluationBatchManifest::quote)
                .collect(java.util.stream.Collectors.joining(","));
        String budgets = budgetProfiles.stream().map(EvaluationBatchManifest::quote)
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"recordType\":\"batchManifest\",\"baselineId\":" + quote(baselineId)
                + ",\"corpusVersion\":" + quote(corpusVersion)
                + ",\"corpusFingerprint\":" + quote(corpusFingerprint)
                + ",\"worldModelFingerprint\":" + quote(worldModelFingerprint)
                + ",\"evaluatorFingerprint\":" + quote(evaluatorFingerprint)
                + ",\"plannerConfigurationFingerprint\":" + quote(plannerConfigurationFingerprint)
                + ",\"plannerIds\":[" + planners + "],\"scenarioCount\":" + scenarioCount
                + ",\"runCount\":" + runCount + ",\"budgetProfiles\":[" + budgets + "]"
                + ",\"seedPolicy\":" + quote(seedPolicy)
                + ",\"fixtureAsOf\":" + quote(fixtureAsOf.toString())
                + ",\"runMode\":" + quote(runMode) + "}";
    }

    private static String quote(String value) { return "\"" + escape(value) + "\""; }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\"' -> out.append("\\\"");
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
