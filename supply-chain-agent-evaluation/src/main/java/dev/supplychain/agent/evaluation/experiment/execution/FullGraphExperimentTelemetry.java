package dev.supplychain.agent.evaluation.experiment.execution;

import dev.supplychain.agent.evaluation.host.FullGraphRuntime;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentScenarioUnit;
import dev.supplychain.agent.mechanisms.b1.B1MemoryFeatureState;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Built-in graph/mechanism telemetry. These are descriptive run signals, not primary endpoints. */
public final class FullGraphExperimentTelemetry implements ExperimentMetricCollector {
    @Override
    public Map<String, Double> collect(ExperimentScenarioUnit unit, ExperimentMechanism mechanism,
            ExperimentArm arm, FullGraphRuntime runtime, CognitiveCycleState state) {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(mechanism, "mechanism");
        Objects.requireNonNull(arm, "arm");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(state, "state");
        var metrics = new LinkedHashMap<String, Double>();
        metrics.put("graph.evidenceRequestCount", (double) state.integer(CognitiveCycleState.EVIDENCE_REQUEST_COUNT));
        metrics.put("graph.plannerCandidateCount", (double) state.integer(CognitiveCycleState.PLANNER_CANDIDATE_COUNT));
        metrics.put("graph.planningRound", (double) state.integer(CognitiveCycleState.PLANNING_ROUND));
        metrics.put("graph.reconsiderationCount", (double) state.integer(CognitiveCycleState.RECONSIDERATION_COUNT));
        metrics.put("graph.metaControlRounds", (double) state.integer(CognitiveCycleState.META_CONTROL_ROUND));
        metrics.put("graph.eventCount", (double) state.events().size());
        metrics.put("graph.approvalBoundaryReached", state.text(CognitiveCycleState.APPROVAL_ID).isBlank() ? 0.0 : 1.0);
        metrics.put("graph.clarificationBoundaryReached",
                "NEEDS_CLARIFICATION".equals(state.text(CognitiveCycleState.DECISION_STATUS)) ? 1.0 : 0.0);

        MemoryScopeId scope = unit.fixture().planningRequest().workingMemory().scopeId();
        var features = runtime.b1MemoryFeatureRepository().snapshot().stream()
                .filter(feature -> feature.reference().scopeId().equals(scope)).toList();
        metrics.put("b1.featureStateCount", (double) features.size());
        metrics.put("b1.accessEvents", (double) features.stream().mapToLong(v -> v.appliedAccessEventKeys().size()).sum());
        metrics.put("b1.verifiedUsefulOutcomes", (double) features.stream().mapToLong(B1MemoryFeatureState::usefulOutcomes).sum());
        metrics.put("b1.verifiedHarmfulOutcomes", (double) features.stream().mapToLong(B1MemoryFeatureState::harmfulOutcomes).sum());
        metrics.put("b1.verifiedNeutralOutcomes", (double) features.stream().mapToLong(B1MemoryFeatureState::neutralOutcomes).sum());
        metrics.put("b1.meanExpectedDownstreamUtility", features.stream().mapToDouble(B1MemoryFeatureState::expectedDownstreamUtility)
                .average().orElse(0.5));

        metrics.put("b2.recoveryProcedureDraftPresent",
                state.text(CognitiveCycleState.RECOVERY_PROCEDURE_DRAFT_ID).isBlank() ? 0.0 : 1.0);
        long downstreamOutcomes = runtime.r2Workflow() == null ? 0
                : runtime.r2Workflow().b3OutcomeEvidenceRepository().findOutcomes(scope, 10_000).size();
        long coalitionOutcomes = runtime.r2Workflow() == null ? 0
                : runtime.r2Workflow().b3OutcomeEvidenceRepository().findCoalitionOutcomes(scope, 10_000).size();
        metrics.put("b3.verifiedDownstreamOutcomeCount", (double) downstreamOutcomes);
        metrics.put("b3.verifiedCoalitionOutcomeCount", (double) coalitionOutcomes);

        metrics.put("b4.intentionPresent", state.text(CognitiveCycleState.INTENTION_ID).isBlank() ? 0.0 : 1.0);
        metrics.put("b4.intentionVersion", (double) state.number(CognitiveCycleState.INTENTION_VERSION));
        metrics.put("b4.objectiveFingerprintPresent",
                state.text(CognitiveCycleState.INTENTION_OBJECTIVE_FINGERPRINT).isBlank() ? 0.0 : 1.0);
        metrics.put("b4.methodReferencePresent", state.text(CognitiveCycleState.PLANNING_METHOD_ID).isBlank() ? 0.0 : 1.0);

        metrics.put("b6.uncertaintyAssessmentPresent",
                state.uncertaintyAssessmentReference() == null ? 0.0 : 1.0);
        metrics.put("b6.evidenceMaximumUncertainty", state.<Double>value(CognitiveCycleState.EVIDENCE_MAXIMUM_UNCERTAINTY).orElse(0.0));
        metrics.put("b6.metaControlMaximumUncertainty", state.<Double>value(CognitiveCycleState.META_CONTROL_MAXIMUM_UNCERTAINTY).orElse(0.0));
        metrics.put("b6.calibrationServiceInjected", runtime.uncertaintyCalibration() == null ? 0.0 : 1.0);
        return Map.copyOf(metrics);
    }
}
