package dev.supplychain.agent.evaluation.experiment.host;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMetricCollector;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentSandboxAudit;
import dev.supplychain.agent.evaluation.host.FullGraphEvaluationHost;
import dev.supplychain.agent.mechanisms.b1.B1MemoryFeatureState;
import dev.supplychain.agent.mechanisms.b2.ReusableRecoveryProcedure;
import dev.supplychain.agent.mechanisms.b3.WorkspaceCoalitionOutcomeAdjustment;

import java.util.List;
import java.util.Objects;

/** Immutable, per-arm configuration used to compile one isolated deterministic TRAIN graph. */
public record DeterministicTrainSessionConfiguration(
        FullGraphEvaluationHost.Ports ports,
        String conditionFingerprint,
        ExperimentSandboxAudit sandboxAudit,
        ExperimentMetricCollector metricCollector,
        List<B1MemoryFeatureState> b1FeatureSeeds,
        WorkspaceCoalitionOutcomeAdjustment b3ShadowAdjustment,
        List<ReusableRecoveryProcedure> b2ProcedureSeeds) {

    public DeterministicTrainSessionConfiguration {
        ports = Objects.requireNonNull(ports, "ports");
        conditionFingerprint = Objects.requireNonNull(conditionFingerprint, "conditionFingerprint").trim();
        if (!conditionFingerprint.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("conditionFingerprint must be lowercase SHA-256");
        sandboxAudit = Objects.requireNonNull(sandboxAudit, "sandboxAudit");
        metricCollector = Objects.requireNonNull(metricCollector, "metricCollector");
        b1FeatureSeeds = List.copyOf(Objects.requireNonNull(b1FeatureSeeds, "b1FeatureSeeds"));
        b3ShadowAdjustment = Objects.requireNonNull(b3ShadowAdjustment, "b3ShadowAdjustment");
        b2ProcedureSeeds = List.copyOf(Objects.requireNonNull(b2ProcedureSeeds, "b2ProcedureSeeds"));
    }
}
