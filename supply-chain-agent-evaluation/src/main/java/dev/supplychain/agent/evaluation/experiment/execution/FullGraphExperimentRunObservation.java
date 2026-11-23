package dev.supplychain.agent.evaluation.experiment.execution;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Raw observation from one actual full-graph run; it is not an outcome label or causal estimate. */
public record FullGraphExperimentRunObservation(
        String sessionId,
        String experimentUnitId,
        String fixtureFingerprint,
        String effectiveInputFingerprint,
        String requestFingerprint,
        ExperimentMechanism mechanism,
        ExperimentArm arm,
        int executionOrder,
        Boundary boundary,
        String decisionStatus,
        String graphLastReason,
        Map<String, String> stateSignals,
        List<String> graphEvents,
        String graphEventFingerprint,
        Map<String, Double> metrics,
        ExperimentSandboxAudit.Snapshot sideEffectDelta,
        String observationFingerprint) {
    public enum Boundary { APPROVAL, CLARIFICATION, TERMINAL, OTHER_INTERRUPTION }

    public FullGraphExperimentRunObservation {
        sessionId = required(sessionId, "sessionId");
        experimentUnitId = required(experimentUnitId, "experimentUnitId");
        fixtureFingerprint = required(fixtureFingerprint, "fixtureFingerprint");
        effectiveInputFingerprint = required(effectiveInputFingerprint, "effectiveInputFingerprint");
        requestFingerprint = required(requestFingerprint, "requestFingerprint");
        mechanism = Objects.requireNonNull(mechanism, "mechanism");
        arm = Objects.requireNonNull(arm, "arm");
        if (executionOrder < 1 || executionOrder > 2) throw new IllegalArgumentException("executionOrder must be 1 or 2");
        boundary = Objects.requireNonNull(boundary, "boundary");
        decisionStatus = Objects.requireNonNull(decisionStatus, "decisionStatus");
        graphLastReason = Objects.requireNonNull(graphLastReason, "graphLastReason");
        stateSignals = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(stateSignals, "stateSignals")));
        graphEvents = List.copyOf(Objects.requireNonNull(graphEvents, "graphEvents"));
        graphEventFingerprint = required(graphEventFingerprint, "graphEventFingerprint");
        var normalizedMetrics = new TreeMap<String, Double>();
        Objects.requireNonNull(metrics, "metrics").forEach((key, value) -> {
            String metric = required(key, "metric key");
            if (value == null || !Double.isFinite(value)) throw new IllegalArgumentException("metric must be finite: " + metric);
            normalizedMetrics.put(metric, value);
        });
        metrics = Collections.unmodifiableMap(normalizedMetrics);
        sideEffectDelta = Objects.requireNonNull(sideEffectDelta, "sideEffectDelta");
        if (!sideEffectDelta.isSideEffectFree()) throw new SecurityException("graph run contains a prohibited side effect");
        observationFingerprint = required(observationFingerprint, "observationFingerprint");
    }

    public static String fingerprint(String canonical) { return sha256(canonical); }

    public String toJsonLine() {
        String state = stateSignals.entrySet().stream().map(entry -> quote(entry.getKey()) + ":" + quote(entry.getValue()))
                .collect(java.util.stream.Collectors.joining(","));
        String metricJson = metrics.entrySet().stream().map(entry -> quote(entry.getKey()) + ":" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(","));
        String events = graphEvents.stream().map(FullGraphExperimentRunObservation::quote)
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"recordType\":\"fullGraphRun\","
                + "\"sessionId\":" + quote(sessionId) + ","
                + "\"experimentUnitId\":" + quote(experimentUnitId) + ","
                + "\"fixtureFingerprint\":" + quote(fixtureFingerprint) + ","
                + "\"effectiveInputFingerprint\":" + quote(effectiveInputFingerprint) + ","
                + "\"requestFingerprint\":" + quote(requestFingerprint) + ","
                + "\"mechanism\":" + quote(mechanism.name()) + ","
                + "\"arm\":" + quote(arm.name()) + ","
                + "\"executionOrder\":" + executionOrder + ","
                + "\"boundary\":" + quote(boundary.name()) + ","
                + "\"decisionStatus\":" + quote(decisionStatus) + ","
                + "\"graphLastReason\":" + quote(graphLastReason) + ","
                + "\"stateSignals\":{" + state + "},"
                + "\"graphEvents\":[" + events + "],"
                + "\"graphEventFingerprint\":" + quote(graphEventFingerprint) + ","
                + "\"metrics\":{" + metricJson + "},"
                + "\"sideEffectDelta\":{\"externalNetworkCalls\":" + sideEffectDelta.externalNetworkCalls()
                + ",\"externalActionDispatches\":" + sideEffectDelta.externalActionDispatches()
                + ",\"stateChangingDispatches\":" + sideEffectDelta.stateChangingDispatches()
                + ",\"dispatchesAfterApprovalBoundary\":" + sideEffectDelta.dispatchesAfterApprovalBoundary() + "},"
                + "\"observationFingerprint\":" + quote(observationFingerprint) + "}";
    }

    static String quote(String value) { return "\"" + escape(value) + "\""; }
    static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch)); else out.append(ch); }
            }
        }
        return out.toString();
    }
    private static String required(String value, String field) {
        String result = Objects.requireNonNull(value, field).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return result;
    }
}
