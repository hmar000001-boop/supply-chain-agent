package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentArm;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import dev.supplychain.agent.evaluation.experiment.execution.FullGraphExperimentAdapter;
import dev.supplychain.agent.evaluation.experiment.execution.FullGraphExperimentPair;
import dev.supplychain.agent.evaluation.experiment.execution.FullGraphExperimentSession;
import dev.supplychain.agent.evaluation.experiment.execution.FullGraphExperimentSessionFactory;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentScenarioUnit;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import dev.supplychain.agent.evaluation.experiment.scenario.M8ExperimentScenarioFactory;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleState;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Confirmatory, mechanism-specific HOLDOUT harness. TRAIN artifacts are verified before any
 * graph session is created. The treatment settings are bound to the actual graph factory, and
 * each arm emits typed mechanism evidence plus an independent simulator replay.
 */
public final class MechanismSpecificExperimentHarness {
    private final String candidateCodeFingerprint;
    private final MechanismTrainingArtifactBundle trainingArtifacts;
    private final MechanismTrainingArtifactVerifier trainingArtifactVerifier;
    private final MechanismSpecificExperimentSessionFactory sessionFactory;
    private final MechanismExperimentPlanFactory planFactory;
    private final IndependentSimulatorPlanVerifier simulatorVerifier;
    private final FullGraphMechanismEvidenceCollector evidenceCollector;
    private final MechanismSpecificExperimentRunVerifier runVerifier;
    private final B6HoldoutLabelProducer b6LabelProducer;

    public MechanismSpecificExperimentHarness(String candidateCodeFingerprint,
            MechanismTrainingArtifactBundle trainingArtifacts,
            MechanismTrainingArtifactVerifier trainingArtifactVerifier,
            MechanismSpecificExperimentSessionFactory sessionFactory,
            IndependentSimulatorPlanVerifier simulatorVerifier,
            SimulatorUncertaintyLabelVerifier b6HoldoutLabelVerifier,
            B6HoldoutLabelProducer b6LabelProducer) {
        this.candidateCodeFingerprint = ExperimentEvidenceFingerprint.requireSha(candidateCodeFingerprint, "candidateCodeFingerprint");
        this.trainingArtifacts = Objects.requireNonNull(trainingArtifacts, "trainingArtifacts");
        this.trainingArtifactVerifier = Objects.requireNonNull(trainingArtifactVerifier, "trainingArtifactVerifier");
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
        this.simulatorVerifier = Objects.requireNonNull(simulatorVerifier, "simulatorVerifier");
        this.planFactory = new MechanismExperimentPlanFactory();
        this.evidenceCollector = new FullGraphMechanismEvidenceCollector();
        this.runVerifier = new MechanismSpecificExperimentRunVerifier(
                Objects.requireNonNull(b6HoldoutLabelVerifier, "b6HoldoutLabelVerifier"));
        this.b6LabelProducer = b6LabelProducer;
    }

    /**
     * Executes one exact preregistered HOLDOUT unit. The method re-verifies the frozen TRAIN
     * bundle on every invocation so a caller cannot mutate or replace it after harness creation.
     */
    public MechanismSpecificExperimentPair runHoldoutPair(ExperimentScenarioUnit unit,
            ExperimentMechanism mechanism) throws Exception {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(mechanism, "mechanism");
        if (unit.split() != ExperimentSplit.HOLDOUT || !M8ExperimentScenarioFactory.PROTOCOL_ID.equals(unit.protocolId()))
            throw new SecurityException("mechanism-specific confirmatory harness accepts HOLDOUT units only");
        if (!candidateCodeFingerprint.equals(trainingArtifacts.candidateCodeFingerprint()))
            throw new SecurityException("frozen TRAIN artifacts belong to a different source candidate fingerprint");
        trainingArtifactVerifier.verify(trainingArtifacts);

        Map<ExperimentArm, MechanismSpecificExperimentEvidence> mechanismEvidence = new EnumMap<>(ExperimentArm.class);
        Map<ExperimentArm, SimulatorPlanOutcomeEvidence> simulatorEvidence = new EnumMap<>(ExperimentArm.class);
        MechanismSpecificExperimentSessionFactory plannedFactory = (scenario, selectedMechanism, arm) -> {
            MechanismArmPlan plan = planFactory.create(scenario, selectedMechanism,
                    arm, trainingArtifacts);
            FullGraphExperimentSession session = sessionFactory.create(scenario, plan, trainingArtifacts);
            if (!session.conditionFingerprint().equals(plan.planFingerprint())) {
                try { session.close(); } catch (RuntimeException ignored) { }
                throw new SecurityException("session factory did not bind the actual graph condition to the exact arm plan fingerprint");
            }
            if (session.mechanism() != selectedMechanism || session.arm() != arm
                    || !session.unit().effectiveInputFingerprint().equals(scenario.effectiveInputFingerprint())) {
                try { session.close(); } catch (RuntimeException ignored) { }
                throw new SecurityException("session factory returned a graph for a different mechanism, arm or input");
            }
            return session;
        };
        FullGraphExperimentSessionFactory graphFactory = (scenario, selectedMechanism, arm) ->
                plannedFactory.create(scenario, selectedMechanism, arm);
        FullGraphExperimentAdapter adapter = FullGraphExperimentAdapter.forFrozenTrainingArtifactHoldout(
                graphFactory, trainingArtifacts, trainingArtifactVerifier);
        FullGraphExperimentPair graphPair = adapter.runHoldoutPair(unit, mechanism,
                (session, terminalState, observation) -> capture(session, terminalState, mechanismEvidence, simulatorEvidence));

        List<B6SimulatorLabelEvidence> labels = List.of();
        if (mechanism == ExperimentMechanism.B6_UNCERTAINTY_CALIBRATION) {
            if (b6LabelProducer == null)
                throw new SecurityException("B6 HOLDOUT cannot run without an independent domain-specific label producer");
            var baselinePrediction = requireType(mechanismEvidence.get(ExperimentArm.BASELINE), B6CalibrationRunEvidence.class);
            labels = List.copyOf(Objects.requireNonNull(b6LabelProducer.produce(unit, baselinePrediction,
                    simulatorEvidence.get(ExperimentArm.BASELINE)), "B6 label producer returned null"));
        }
        return runVerifier.verify(graphPair, mechanism, trainingArtifacts,
                mechanismEvidence.get(ExperimentArm.BASELINE), mechanismEvidence.get(ExperimentArm.TREATMENT),
                simulatorEvidence.get(ExperimentArm.BASELINE), simulatorEvidence.get(ExperimentArm.TREATMENT), labels);
    }

    private void capture(FullGraphExperimentSession session, CognitiveCycleState state,
            Map<ExperimentArm, MechanismSpecificExperimentEvidence> mechanismEvidence,
            Map<ExperimentArm, SimulatorPlanOutcomeEvidence> simulatorEvidence) throws Exception {
        SimulatorPlanOutcomeEvidence simulator = simulatorVerifier.verify(session, state);
        MechanismSpecificExperimentEvidence mechanism = evidenceCollector.capture(session, state, trainingArtifacts);
        if (mechanism.arm() != session.arm() || mechanism.mechanism() != session.mechanism()
                || !mechanism.experimentUnitFingerprint().equals(session.unit().effectiveInputFingerprint())
                || !mechanism.trainingArtifactFingerprint().equals(trainingArtifacts.artifactFingerprint()))
            throw new SecurityException("mechanism evidence collector returned evidence outside the active graph session");
        if (mechanismEvidence.putIfAbsent(session.arm(), mechanism) != null
                || simulatorEvidence.putIfAbsent(session.arm(), simulator) != null)
            throw new SecurityException("duplicate arm evidence callback for one matched unit");
    }

    private static <T> T requireType(Object value, Class<T> type) {
        if (!type.isInstance(value)) throw new SecurityException("missing exact typed mechanism evidence: " + type.getSimpleName());
        return type.cast(value);
    }

    public MechanismTrainingArtifactBundle trainingArtifacts() { return trainingArtifacts; }
}
