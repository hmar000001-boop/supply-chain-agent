package dev.supplychain.agent.evaluation.experiment.execution;

import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentScenarioUnit;
import dev.supplychain.agent.evaluation.host.FullGraphEvaluationHost;
import dev.supplychain.agent.evaluation.host.FullGraphRuntime;
import dev.supplychain.agent.mechanisms.b6.UncertaintyCalibrationService;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.RunnableConfig;

import java.util.Map;
import java.util.Objects;

/** One disposable, fully compiled LangGraph4j cognitive-cycle instance for one matched condition. */
public record FullGraphExperimentSession(
        ExperimentScenarioUnit unit,
        ExperimentMechanism mechanism,
        ExperimentArm arm,
        String sessionId,
        String isolationNamespace,
        String checkpointThreadId,
        String conditionFingerprint,
        FullGraphEvaluationHost.Ports ports,
        FullGraphRuntime runtime,
        RunnableConfig config,
        Map<String, Object> graphInput,
        ExperimentSandboxAttestation sandboxAttestation,
        ExperimentSandboxAudit sandboxAudit,
        ExperimentMetricCollector metricCollector) implements AutoCloseable {
    public FullGraphExperimentSession {
        unit = Objects.requireNonNull(unit, "unit");
        mechanism = Objects.requireNonNull(mechanism, "mechanism");
        arm = Objects.requireNonNull(arm, "arm");
        sessionId = required(sessionId, "sessionId");
        isolationNamespace = required(isolationNamespace, "isolationNamespace");
        checkpointThreadId = required(checkpointThreadId, "checkpointThreadId");
        conditionFingerprint = required(conditionFingerprint, "conditionFingerprint");
        ports = Objects.requireNonNull(ports, "ports");
        runtime = Objects.requireNonNull(runtime, "runtime");
        config = Objects.requireNonNull(config, "config");
        graphInput = Map.copyOf(Objects.requireNonNull(graphInput, "graphInput"));
        sandboxAttestation = Objects.requireNonNull(sandboxAttestation, "sandboxAttestation");
        sandboxAudit = Objects.requireNonNull(sandboxAudit, "sandboxAudit");
        metricCollector = Objects.requireNonNull(metricCollector, "metricCollector");
        validateInput(unit, graphInput);
        if (runtime.graph() == null) throw new IllegalArgumentException("full graph runtime has no compiled graph");
    }


    /** Compiles this condition against the real application graph with a fresh in-memory checkpoint saver. */
    public static FullGraphExperimentSession compile(ExperimentScenarioUnit unit, ExperimentMechanism mechanism,
            ExperimentArm arm, String sessionId, String isolationNamespace, String checkpointThreadId,
            String conditionFingerprint, FullGraphEvaluationHost host, FullGraphEvaluationHost.Ports ports,
            RunnableConfig config, Map<String, Object> graphInput, ExperimentSandboxAttestation attestation,
            ExperimentSandboxAudit audit, ExperimentMetricCollector collector,
            UncertaintyCalibrationService shadowCalibration) throws Exception {
        Objects.requireNonNull(host, "host");
        FullGraphRuntime runtime = host.compileFullRuntime(ports, ignored -> new MemorySaver(), shadowCalibration);
        return new FullGraphExperimentSession(unit, mechanism, arm, sessionId, isolationNamespace,
                checkpointThreadId, conditionFingerprint, ports, runtime, config, graphInput,
                attestation, audit, collector);
    }

    @Override public void close() {
        // Current host uses an in-memory checkpoint saver and in-memory stores. A future durable
        // experiment host may wrap its resources behind a session factory that closes them here.
    }

    private static void validateInput(ExperimentScenarioUnit unit, Map<String, Object> input) {
        String execution = unit.fixture().planningRequest().executionId();
        String scope = unit.fixture().planningRequest().workingMemory().scopeId().value();
        if (!execution.equals(input.get("executionId")) || !scope.equals(input.get("memoryScopeId"))) {
            throw new SecurityException("full-graph input execution/scope does not match the generated experiment unit");
        }
        if (!(input.get("requestId") instanceof String requestId) || requestId.isBlank()
                || !(input.get("request") instanceof String request) || request.isBlank()) {
            throw new IllegalArgumentException("full-graph experiment input requires requestId and request");
        }
        for (String key : input.keySet()) {
            String normalized = key.toLowerCase(java.util.Locale.ROOT);
            if (normalized.contains("approval") || normalized.contains("approver")
                    || normalized.contains("humancontrol") || normalized.contains("authorizationresponse")) {
                throw new SecurityException("experiment graph input must not pre-authorize or resume an approval boundary");
            }
        }
    }

    private static String required(String value, String field) {
        String result = Objects.requireNonNull(value, field).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return result;
    }
}
