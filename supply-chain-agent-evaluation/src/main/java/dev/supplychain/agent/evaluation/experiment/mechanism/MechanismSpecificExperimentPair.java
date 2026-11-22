package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentArm;
import dev.supplychain.agent.evaluation.experiment.execution.FullGraphExperimentPair;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import java.util.List;
import java.util.Objects;

/** One matched graph pair plus independently replayed simulator outcomes and typed mechanism evidence. */
public record MechanismSpecificExperimentPair(
        FullGraphExperimentPair graphPair,
        MechanismSpecificExperimentEvidence baselineMechanismEvidence,
        MechanismSpecificExperimentEvidence treatmentMechanismEvidence,
        SimulatorPlanOutcomeEvidence baselineSimulatorEvidence,
        SimulatorPlanOutcomeEvidence treatmentSimulatorEvidence,
        List<B6SimulatorLabelEvidence> b6HoldoutLabels,
        String evidenceFingerprint) {
    public MechanismSpecificExperimentPair {
        graphPair = Objects.requireNonNull(graphPair, "graphPair");
        baselineMechanismEvidence = Objects.requireNonNull(baselineMechanismEvidence, "baselineMechanismEvidence");
        treatmentMechanismEvidence = Objects.requireNonNull(treatmentMechanismEvidence, "treatmentMechanismEvidence");
        baselineSimulatorEvidence = Objects.requireNonNull(baselineSimulatorEvidence, "baselineSimulatorEvidence");
        treatmentSimulatorEvidence = Objects.requireNonNull(treatmentSimulatorEvidence, "treatmentSimulatorEvidence");
        b6HoldoutLabels = List.copyOf(Objects.requireNonNull(b6HoldoutLabels, "b6HoldoutLabels"));
        ExperimentMechanism mechanism = graphPair.mechanism();
        String unit = graphPair.effectiveInputFingerprint();
        requireArm(baselineMechanismEvidence, mechanism, ExperimentArm.BASELINE, unit, "baseline mechanism");
        requireArm(treatmentMechanismEvidence, mechanism, ExperimentArm.TREATMENT, unit, "treatment mechanism");
        requireSimulator(baselineSimulatorEvidence, mechanism, ExperimentArm.BASELINE, unit, "baseline simulator");
        requireSimulator(treatmentSimulatorEvidence, mechanism, ExperimentArm.TREATMENT, unit, "treatment simulator");
        if (!baselineSimulatorEvidence.worldModelFingerprint().equals(treatmentSimulatorEvidence.worldModelFingerprint())
                || !baselineSimulatorEvidence.evaluatorFingerprint().equals(treatmentSimulatorEvidence.evaluatorFingerprint()))
            throw new SecurityException("matched independent replays used different simulator/evaluator configurations");
        if (mechanism == ExperimentMechanism.B6_UNCERTAINTY_CALIBRATION && b6HoldoutLabels.isEmpty())
            throw new SecurityException("B6 confirmatory pair requires independent holdout error labels");
        if (mechanism != ExperimentMechanism.B6_UNCERTAINTY_CALIBRATION && !b6HoldoutLabels.isEmpty())
            throw new SecurityException("B6 labels cannot be attached to a different mechanism study");
        String expected = calculate(graphPair, baselineMechanismEvidence, treatmentMechanismEvidence,
                baselineSimulatorEvidence, treatmentSimulatorEvidence, b6HoldoutLabels);
        evidenceFingerprint = ExperimentEvidenceFingerprint.requireSha(evidenceFingerprint, "evidenceFingerprint");
        if (!evidenceFingerprint.equals(expected)) throw new SecurityException("mechanism experiment pair fingerprint mismatch");
    }

    /** Labels used for B6 validation on either HOLDOUT or TRANSFER; legacy component name retained for JSON compatibility. */
    public List<B6SimulatorLabelEvidence> b6ValidationLabels() { return b6HoldoutLabels; }

    public static MechanismSpecificExperimentPair create(FullGraphExperimentPair graphPair,
            MechanismSpecificExperimentEvidence baselineMechanism,
            MechanismSpecificExperimentEvidence treatmentMechanism,
            SimulatorPlanOutcomeEvidence baselineSimulator,
            SimulatorPlanOutcomeEvidence treatmentSimulator,
            List<B6SimulatorLabelEvidence> labels) {
        return new MechanismSpecificExperimentPair(graphPair, baselineMechanism, treatmentMechanism,
                baselineSimulator, treatmentSimulator, labels,
                calculate(graphPair, baselineMechanism, treatmentMechanism, baselineSimulator, treatmentSimulator, labels));
    }

    public String toJsonLine() {
        String labels = b6HoldoutLabels.stream().map(label -> "{\"split\":\"" + label.split().name()
                + "\",\"experimentUnitFingerprint\":" + q(label.experimentUnitFingerprint())
                + ",\"simulatorRunFingerprint\":" + q(label.simulatorRunFingerprint())
                + ",\"simulatorStateFingerprint\":" + q(label.simulatorStateFingerprint())
                + ",\"sampleId\":" + q(label.observation().sampleId())
                + ",\"dimension\":" + q(label.observation().dimension().name())
                + ",\"predictedUncertainty\":" + label.observation().predictedUncertainty()
                + ",\"realizedError\":" + label.observation().realizedError()
                + ",\"labelEvidence\":" + q(label.observation().labelEvidence().evidenceType() + ":"
                    + label.observation().labelEvidence().evidenceId()) + "}")
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"recordType\":\"mechanismSpecificMatchedPair\",\"graphPair\":" + graphPair.toJsonLine()
                + ",\"baselineMechanismEvidence\":" + baselineMechanismEvidence.toJson()
                + ",\"treatmentMechanismEvidence\":" + treatmentMechanismEvidence.toJson()
                + ",\"baselineSimulatorEvidence\":" + baselineSimulatorEvidence.toJson()
                + ",\"treatmentSimulatorEvidence\":" + treatmentSimulatorEvidence.toJson()
                + ",\"b6HoldoutLabels\":[" + labels + "],\"evidenceFingerprint\":" + q(evidenceFingerprint) + "}";
    }

    private static String calculate(FullGraphExperimentPair graphPair,
            MechanismSpecificExperimentEvidence baselineMechanism,
            MechanismSpecificExperimentEvidence treatmentMechanism,
            SimulatorPlanOutcomeEvidence baselineSimulator,
            SimulatorPlanOutcomeEvidence treatmentSimulator, List<B6SimulatorLabelEvidence> labels) {
        return ExperimentEvidenceFingerprint.sha256(graphPair.pairFingerprint() + "|"
                + baselineMechanism.evidenceFingerprint() + "|" + treatmentMechanism.evidenceFingerprint() + "|"
                + baselineSimulator.evidenceFingerprint() + "|" + treatmentSimulator.evidenceFingerprint() + "|"
                + labels.stream().map(label -> label.observation().sampleId() + ":"
                        + label.observation().labelEvidence().evidenceId()).sorted().toList());
    }

    private static void requireArm(MechanismSpecificExperimentEvidence evidence, ExperimentMechanism mechanism,
            ExperimentArm arm, String unit, String label) {
        if (evidence.mechanism() != mechanism || evidence.arm() != arm
                || !evidence.experimentUnitFingerprint().equals(unit))
            throw new SecurityException(label + " evidence is bound to a different mechanism, arm or input fingerprint");
    }

    private static void requireSimulator(SimulatorPlanOutcomeEvidence evidence, ExperimentMechanism mechanism,
            ExperimentArm arm, String unit, String label) {
        if (evidence.mechanism() != mechanism || evidence.arm() != arm
                || !evidence.experimentUnitFingerprint().equals(unit)
                || evidence.status() == SimulatorPlanOutcomeEvidence.Status.REPLAY_REJECTED
                || !"SIMULATOR_ONLY_NOT_BUSINESS_OUTCOME".equals(evidence.evidenceClass()))
            throw new SecurityException(label + " evidence failed independent simulator replay admission");
    }

    private static String q(String value) {
        return "\"" + Objects.requireNonNullElse(value, "").replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
