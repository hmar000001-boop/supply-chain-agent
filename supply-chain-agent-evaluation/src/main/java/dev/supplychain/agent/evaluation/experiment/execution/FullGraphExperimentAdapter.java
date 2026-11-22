package dev.supplychain.agent.evaluation.experiment.execution;

import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentScenarioUnit;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import dev.supplychain.agent.evaluation.experiment.scenario.M8ExperimentScenarioFactory;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleState;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Matched-pair executor for the actual application LangGraph4j graph. Version 1 is TRAIN-only:
 * HOLDOUT and TRANSFER fail closed until the training-artifact freeze and holdout-completion gates
 * are implemented. Every arm gets a fresh host, fresh stateful ports, a unique checkpoint thread,
 * and a runtime-audited sandbox. Runs stop at the first graph boundary and are never resumed.
 */
public final class FullGraphExperimentAdapter {
    private final FullGraphExperimentSessionFactory sessionFactory;
    private final Map<String, ExperimentScenarioUnit> preregisteredTrainingUnits;

    public FullGraphExperimentAdapter(FullGraphExperimentSessionFactory sessionFactory) {
        this(sessionFactory, M8ExperimentScenarioFactory.loadDefault().generate(ExperimentSplit.TRAIN));
    }

    public FullGraphExperimentAdapter(FullGraphExperimentSessionFactory sessionFactory,
                                      List<ExperimentScenarioUnit> preregisteredTrainingUnits) {
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
        List<ExperimentScenarioUnit> authorized = List.copyOf(Objects.requireNonNull(
                preregisteredTrainingUnits, "preregisteredTrainingUnits"));
        if (authorized.size() != 280 || authorized.stream().anyMatch(unit -> unit.split() != ExperimentSplit.TRAIN
                || !M8ExperimentScenarioFactory.PROTOCOL_ID.equals(unit.protocolId()))) {
            throw new SecurityException("adapter must be bound to the exact preregistered 280-unit TRAIN set");
        }
        var byId = new java.util.TreeMap<String, ExperimentScenarioUnit>();
        for (ExperimentScenarioUnit unit : authorized) {
            if (byId.putIfAbsent(unit.experimentUnitId(), unit) != null)
                throw new SecurityException("duplicate preregistered TRAIN unit ID");
        }
        this.preregisteredTrainingUnits = Map.copyOf(byId);
    }

    public FullGraphExperimentPair runTrainingPair(ExperimentScenarioUnit unit, ExperimentMechanism mechanism)
            throws Exception {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(mechanism, "mechanism");
        if (!M8ExperimentScenarioFactory.PROTOCOL_ID.equals(unit.protocolId()))
            throw new SecurityException("experiment unit protocol does not match the preregistered protocol");
        if (unit.split() != ExperimentSplit.TRAIN)
            throw new SecurityException("full-graph adapter v1 may execute TRAIN only; HOLDOUT/TRANSFER gates are not open");
        ExperimentScenarioUnit registered = preregisteredTrainingUnits.get(unit.experimentUnitId());
        if (registered == null || !registered.fixture().fixtureFingerprint().equals(unit.fixture().fixtureFingerprint())
                || !registered.effectiveInputFingerprint().equals(unit.effectiveInputFingerprint())
                || registered.seed() != unit.seed() || !registered.fixtureProfile().equals(unit.fixtureProfile())
                || !registered.scenarioFamily().equals(unit.scenarioFamily())) {
            throw new SecurityException("experiment unit is not byte-for-byte equivalent to its preregistered TRAIN fixture identity");
        }

        FullGraphExperimentSession baseline = null;
        FullGraphExperimentSession treatment = null;
        try {
            baseline = sessionFactory.create(unit, mechanism, ExperimentArm.BASELINE);
            treatment = sessionFactory.create(unit, mechanism, ExperimentArm.TREATMENT);
            validateSessionIdentity(baseline, unit, mechanism, ExperimentArm.BASELINE);
            validateSessionIdentity(treatment, unit, mechanism, ExperimentArm.TREATMENT);
            validateIsolationAndMatching(baseline, treatment);

            List<ExperimentArm> order = executionOrder(unit, mechanism);
            var observations = new LinkedHashMap<ExperimentArm, FullGraphExperimentRunObservation>();
            int position = 1;
            for (ExperimentArm arm : order) {
                FullGraphExperimentSession session = arm == ExperimentArm.BASELINE ? baseline : treatment;
                observations.put(arm, execute(session, position++));
            }
            FullGraphExperimentRunObservation baselineResult = observations.get(ExperimentArm.BASELINE);
            FullGraphExperimentRunObservation treatmentResult = observations.get(ExperimentArm.TREATMENT);
            String pairCanonical = unit.protocolId() + "|" + unit.experimentUnitId() + "|" + mechanism + "|"
                    + baselineResult.observationFingerprint() + "|" + treatmentResult.observationFingerprint();
            return new FullGraphExperimentPair(unit.protocolId(), unit.experimentUnitId(), unit.scenarioFamily(),
                    unit.fixtureProfile(), unit.fixture().fixtureFingerprint(), unit.effectiveInputFingerprint(),
                    mechanism, baselineResult, treatmentResult,
                    Map.of(), FullGraphExperimentRunObservation.fingerprint(pairCanonical));
        } finally {
            closeQuietly(treatment);
            closeQuietly(baseline);
        }
    }

    private static FullGraphExperimentRunObservation execute(FullGraphExperimentSession session, int executionOrder)
            throws Exception {
        var auditBefore = Objects.requireNonNull(session.sandboxAudit().snapshot(), "sandbox audit before run");
        var stream = session.runtime().graph().stream(GraphInput.args(session.graphInput()), session.config());
        for (var ignored : stream) { /* execute the actual application graph to its first terminal/interruption boundary */ }
        boolean interrupted = GraphResult.from(stream).isInterruptionMetadata();
        CognitiveCycleState state = session.runtime().graph().getState(session.config()).state();
        var auditDelta = Objects.requireNonNull(session.sandboxAudit().snapshot(), "sandbox audit after run").minus(auditBefore);
        if (!auditDelta.isSideEffectFree()) {
            throw new SecurityException("full-graph run attempted an external or state-changing effect: " + auditDelta);
        }
        rejectCalibratedB6State(session, state);
        FullGraphExperimentRunObservation.Boundary boundary;
        if (!interrupted) boundary = FullGraphExperimentRunObservation.Boundary.TERMINAL;
        else if (!state.text(CognitiveCycleState.APPROVAL_ID).isBlank())
            boundary = FullGraphExperimentRunObservation.Boundary.APPROVAL;
        else if ("NEEDS_CLARIFICATION".equals(state.text(CognitiveCycleState.DECISION_STATUS)))
            boundary = FullGraphExperimentRunObservation.Boundary.CLARIFICATION;
        else boundary = FullGraphExperimentRunObservation.Boundary.OTHER_INTERRUPTION;

        Map<String, Double> metrics = new TreeMap<>(Objects.requireNonNull(session.metricCollector().collect(
                session.unit(), session.mechanism(), session.arm(), session.runtime(), state), "metric collector returned null"));
        if (metrics.isEmpty()) throw new IllegalStateException("full-graph run produced no metric telemetry");
        metrics.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || !Double.isFinite(value))
                throw new IllegalArgumentException("full-graph telemetry contains an invalid metric");
        });

        Map<String, String> signals = new TreeMap<>(stateSignals(state, boundary));
        signals.put("conditionFingerprint", session.conditionFingerprint());
        List<String> events = state.events();
        String eventFingerprint = FullGraphExperimentRunObservation.fingerprint(String.join("\n", events));
        String canonical = session.unit().experimentUnitId() + "|"
                + session.unit().fixture().fixtureFingerprint() + "|" + session.unit().effectiveInputFingerprint()
                + "|" + session.mechanism() + "|" + session.arm() + "|" + session.conditionFingerprint()
                + "|" + executionOrder + "|" + boundary + "|" + state.text(CognitiveCycleState.DECISION_STATUS)
                + "|" + eventFingerprint + "|" + new TreeMap<>(metrics);
        String fingerprint = FullGraphExperimentRunObservation.fingerprint(canonical);
        return new FullGraphExperimentRunObservation(session.sessionId(), session.unit().experimentUnitId(),
                session.unit().fixture().fixtureFingerprint(), session.unit().fixture().planningRequest().fingerprint(),
                session.mechanism(), session.arm(), executionOrder, boundary,
                state.text(CognitiveCycleState.DECISION_STATUS), state.text(CognitiveCycleState.LAST_REASON),
                signals, events, eventFingerprint, metrics, auditDelta, fingerprint);
    }

    /**
     * Calibration profiles must remain shadow-only during this adapter phase. The graph may be
     * instrumented with an evaluation calibration service, but any active profile or persisted
     * calibrated assessment invalidates the run before it can be written to an experiment artifact.
     */
    private static void rejectCalibratedB6State(FullGraphExperimentSession session, CognitiveCycleState state) {
        var reference = state.uncertaintyAssessmentReference();
        if (reference == null) return;
        var context = state.context();
        if (session.runtime().uncertaintyCalibration() != null) {
            String targetId = session.ports().lifecycleDomain().uncertaintyCalibrationTargetId(context);
            if (session.runtime().uncertaintyCalibration()
                    .active(new MemoryScopeId(context.memoryScopeId()), targetId).isPresent()) {
                throw new SecurityException("B6 experiment adapter v1 rejects active calibration profiles; calibration remains shadow-only");
            }
        }
        var assessment = session.ports().lifecycleDomain().currentUncertaintyAssessment(context,
                session.ports().clock().instant());
        if (assessment.calibration().status() == UncertaintyAssessment.CalibrationStatus.CALIBRATED) {
            throw new SecurityException("B6 experiment adapter v1 rejects persisted calibrated assessments; calibration remains shadow-only");
        }
    }

    private static Map<String, String> stateSignals(CognitiveCycleState state,
            FullGraphExperimentRunObservation.Boundary boundary) {
        var signals = new TreeMap<String, String>();
        signals.put("boundary", boundary.name());
        signals.put("plannerId", state.text(CognitiveCycleState.PLANNER_ID));
        signals.put("plannerSessionId", state.text(CognitiveCycleState.PLANNER_SESSION_ID));
        signals.put("candidateSetId", state.text(CognitiveCycleState.CANDIDATE_SET_ID));
        signals.put("selectedCandidateId", state.text(CognitiveCycleState.SELECTED_CANDIDATE_ID));
        signals.put("workspaceSnapshotId", state.text(CognitiveCycleState.WORKSPACE_SNAPSHOT_ID));
        signals.put("evidenceSetId", state.text(CognitiveCycleState.EVIDENCE_SET_ID));
        signals.put("intentionId", state.text(CognitiveCycleState.INTENTION_ID));
        signals.put("intentionVersion", Long.toString(state.number(CognitiveCycleState.INTENTION_VERSION)));
        signals.put("planningMethodId", state.text(CognitiveCycleState.PLANNING_METHOD_ID));
        signals.put("objectiveFingerprint", state.text(CognitiveCycleState.INTENTION_OBJECTIVE_FINGERPRINT));
        signals.put("uncertaintyAssessmentId", state.text(CognitiveCycleState.UNCERTAINTY_ASSESSMENT_ID));
        signals.put("uncertaintyAssessmentVersion", Long.toString(state.number(CognitiveCycleState.UNCERTAINTY_ASSESSMENT_VERSION)));
        signals.put("approvalIdPresent", Boolean.toString(!state.text(CognitiveCycleState.APPROVAL_ID).isBlank()));
        signals.put("decisionStatus", state.text(CognitiveCycleState.DECISION_STATUS));
        return Map.copyOf(signals);
    }

    private static void validateSessionIdentity(FullGraphExperimentSession session, ExperimentScenarioUnit unit,
            ExperimentMechanism mechanism, ExperimentArm arm) {
        if (!unit.experimentUnitId().equals(session.unit().experimentUnitId())
                || !unit.fixture().fixtureFingerprint().equals(session.unit().fixture().fixtureFingerprint())
                || !unit.effectiveInputFingerprint().equals(session.unit().effectiveInputFingerprint())
                || unit.seed() != session.unit().seed()
                || !unit.fixtureProfile().equals(session.unit().fixtureProfile())
                || !unit.scenarioFamily().equals(session.unit().scenarioFamily())
                || session.mechanism() != mechanism || session.arm() != arm) {
            throw new SecurityException("session factory returned a graph for the wrong experiment unit or arm");
        }
        if (session.ports().actionRegistry() == null || session.runtime().graph() == null)
            throw new IllegalArgumentException("experiment session must compile the full graph with an action registry");
        if (session.sandboxAttestation() == null) throw new SecurityException("missing sandbox attestation");
    }

    private static void validateIsolationAndMatching(FullGraphExperimentSession baseline,
            FullGraphExperimentSession treatment) {
        requireDistinct(baseline.sessionId(), treatment.sessionId(), "session IDs");
        requireDistinct(baseline.isolationNamespace(), treatment.isolationNamespace(), "isolation namespaces");
        requireDistinct(baseline.checkpointThreadId(), treatment.checkpointThreadId(), "checkpoint thread IDs");
        requireDistinct(baseline.conditionFingerprint(), treatment.conditionFingerprint(), "arm configuration fingerprints");
        requireDistinct(baseline.sandboxAttestation().attestationId(), treatment.sandboxAttestation().attestationId(),
                "sandbox attestations");
        requireDifferentReference(baseline.sandboxAudit(), treatment.sandboxAudit(), "sandbox audit counters");
        requireDifferentReference(baseline.runtime(), treatment.runtime(), "full-graph runtime");
        requireDifferentReference(baseline.runtime().graph(), treatment.runtime().graph(), "compiled graph");
        requireDifferentReference(baseline.ports(), treatment.ports(), "graph ports record");
        requireDifferentReference(baseline.ports().decisionDomain(), treatment.ports().decisionDomain(), "decision domain");
        requireDifferentReference(baseline.ports().lifecycleDomain(), treatment.ports().lifecycleDomain(), "lifecycle domain");
        requireDifferentReference(baseline.ports().authority(), treatment.ports().authority(), "authority adapter");
        requireDifferentReference(baseline.ports().actionRegistry(), treatment.ports().actionRegistry(), "action registry");
        requireDifferentReference(baseline.runtime().b1MemoryFeatureRepository(),
                treatment.runtime().b1MemoryFeatureRepository(), "B1 feature repository");
        requireDifferentReference(baseline.runtime().b1MemoryFeatureLearning(),
                treatment.runtime().b1MemoryFeatureLearning(), "B1 feature learning service");
        requireDifferentReference(baseline.runtime().b1RetrievalSelector(),
                treatment.runtime().b1RetrievalSelector(), "B1 retrieval selector");
        requireDifferentReference(baseline.runtime().b3WorkspaceAdmission(),
                treatment.runtime().b3WorkspaceAdmission(), "B3 workspace repository/service");
        requireDifferentReference(baseline.runtime().b3WorkspaceBroadcast(),
                treatment.runtime().b3WorkspaceBroadcast(), "B3 workspace broadcast service");
        if (baseline.runtime().uncertaintyCalibration() != null || treatment.runtime().uncertaintyCalibration() != null) {
            requireDifferentReference(baseline.runtime().uncertaintyCalibration(),
                    treatment.runtime().uncertaintyCalibration(), "B6 calibration service");
        }
        if ((baseline.ports().r2Workflow() == null) != (treatment.ports().r2Workflow() == null))
            throw new SecurityException("matched arms have different R2 mechanism wiring");
        if (baseline.ports().r2Workflow() != null) {
            requireDifferentReference(baseline.ports().r2Workflow(), treatment.ports().r2Workflow(), "R2 configuration");
            requireDifferentReference(baseline.ports().r2Workflow().recoveryDraftRepository(),
                    treatment.ports().r2Workflow().recoveryDraftRepository(), "B2 draft repository");
            requireDifferentReference(baseline.ports().r2Workflow().b3OutcomeEvidenceRepository(),
                    treatment.ports().r2Workflow().b3OutcomeEvidenceRepository(), "B3 outcome repository");
        }
        requireDifferentReference(baseline.ports().worldModel(), treatment.ports().worldModel(), "world model adapter");
        requireDifferentReference(baseline.ports().candidateEvaluator(), treatment.ports().candidateEvaluator(), "candidate evaluator");
        requireSame(baseline.ports().worldModel().configurationFingerprint(),
                treatment.ports().worldModel().configurationFingerprint(), "world model configuration");
        requireSame(baseline.ports().candidateEvaluator().configurationFingerprint(),
                treatment.ports().candidateEvaluator().configurationFingerprint(), "candidate evaluator configuration");
        requireSame(baseline.ports().actionRegistry().descriptors(), treatment.ports().actionRegistry().descriptors(),
                "action descriptor set");
        if (baseline.ports().plannerStrategies().size() != treatment.ports().plannerStrategies().size())
            throw new SecurityException("matched arms have different planner strategy counts");
        for (int index = 0; index < baseline.ports().plannerStrategies().size(); index++) {
            requireDifferentReference(baseline.ports().plannerStrategies().get(index),
                    treatment.ports().plannerStrategies().get(index), "planner strategy instance " + index);
            requireSame(baseline.ports().plannerStrategies().get(index).id(),
                    treatment.ports().plannerStrategies().get(index).id(), "planner strategy identity " + index);
        }
        if (!baseline.ports().workspaceConsumers().keySet().equals(treatment.ports().workspaceConsumers().keySet()))
            throw new SecurityException("matched arms have different workspace consumer registrations");
        for (String consumerId : baseline.ports().workspaceConsumers().keySet()) {
            requireDifferentReference(baseline.ports().workspaceConsumers().get(consumerId),
                    treatment.ports().workspaceConsumers().get(consumerId), "workspace consumer " + consumerId);
        }
        if (!baseline.unit().fixture().planningRequest().fingerprint()
                .equals(treatment.unit().fixture().planningRequest().fingerprint())) {
            throw new SecurityException("matched arms are not running the same planning request");
        }
        requireSame(baseline.graphInput(), treatment.graphInput(), "graph input map");
    }

    private static List<ExperimentArm> executionOrder(ExperimentScenarioUnit unit, ExperimentMechanism mechanism) {
        String seed = unit.experimentUnitId() + "|" + mechanism.name() + "|condition-order-v1";
        return (sha256(seed)[0] & 1) == 0
                ? List.of(ExperimentArm.BASELINE, ExperimentArm.TREATMENT)
                : List.of(ExperimentArm.TREATMENT, ExperimentArm.BASELINE);
    }

    private static byte[] sha256(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    private static void requireDistinct(String first, String second, String label) {
        if (first.equals(second)) throw new SecurityException("matched arms share " + label);
    }
    private static void requireDifferentReference(Object first, Object second, String label) {
        if (first == second) throw new SecurityException("matched arms share mutable " + label);
    }
    private static void requireSame(Object first, Object second, String label) {
        if (!Objects.equals(first, second)) throw new SecurityException("matched arms differ in controlled " + label);
    }
    private static void closeQuietly(FullGraphExperimentSession session) {
        if (session == null) return;
        try { session.close(); } catch (RuntimeException ignored) { /* execution result remains primary */ }
    }
}
