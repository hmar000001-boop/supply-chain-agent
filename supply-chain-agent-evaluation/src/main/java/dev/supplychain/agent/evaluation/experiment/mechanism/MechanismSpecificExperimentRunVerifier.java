package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentArm;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import dev.supplychain.agent.evaluation.experiment.execution.FullGraphExperimentPair;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import dev.supplychain.agent.mechanisms.b3.B3CoalitionFeedbackCandidate;
import dev.supplychain.agent.mechanisms.b6.UncertaintyDimension;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Independently checks exact treatment exposure and domain evidence before a pair is admitted. */
public final class MechanismSpecificExperimentRunVerifier {
    private final SimulatorUncertaintyLabelVerifier b6HoldoutLabelVerifier;

    public MechanismSpecificExperimentRunVerifier(SimulatorUncertaintyLabelVerifier b6HoldoutLabelVerifier) {
        this.b6HoldoutLabelVerifier = Objects.requireNonNull(b6HoldoutLabelVerifier, "b6HoldoutLabelVerifier");
    }

    public MechanismSpecificExperimentPair verify(FullGraphExperimentPair graphPair,
            ExperimentMechanism mechanism, MechanismTrainingArtifactBundle bundle,
            MechanismSpecificExperimentEvidence baseline,
            MechanismSpecificExperimentEvidence treatment,
            SimulatorPlanOutcomeEvidence baselineSimulator,
            SimulatorPlanOutcomeEvidence treatmentSimulator,
            List<B6SimulatorLabelEvidence> b6Labels) {
        Objects.requireNonNull(graphPair, "graphPair");
        Objects.requireNonNull(mechanism, "mechanism");
        Objects.requireNonNull(bundle, "bundle");
        if (!graphPair.protocolId().equals(bundle.protocolId())
                || !bundle.trainUnitFingerprints().equals(MechanismTrainingArtifactBundle.exactTrainUnitFingerprints()))
            throw new SecurityException("mechanism result is not bound to the frozen TRAIN artifact protocol");
        if (baseline == null || treatment == null || baseline.mechanism() != mechanism || treatment.mechanism() != mechanism
                || baseline.arm() != ExperimentArm.BASELINE || treatment.arm() != ExperimentArm.TREATMENT
                || !baseline.experimentUnitFingerprint().equals(graphPair.effectiveInputFingerprint())
                || !treatment.experimentUnitFingerprint().equals(graphPair.effectiveInputFingerprint())
                || !baseline.trainingArtifactFingerprint().equals(bundle.artifactFingerprint())
                || !treatment.trainingArtifactFingerprint().equals(bundle.artifactFingerprint()))
            throw new SecurityException("typed mechanism observations do not match the exact matched graph pair and artifact bundle");
        if (baselineSimulator == null || treatmentSimulator == null
                || baselineSimulator.mechanism() != mechanism || treatmentSimulator.mechanism() != mechanism
                || baselineSimulator.arm() != ExperimentArm.BASELINE || treatmentSimulator.arm() != ExperimentArm.TREATMENT
                || !baselineSimulator.experimentUnitFingerprint().equals(graphPair.effectiveInputFingerprint())
                || !treatmentSimulator.experimentUnitFingerprint().equals(graphPair.effectiveInputFingerprint())
                || baselineSimulator.status() == SimulatorPlanOutcomeEvidence.Status.REPLAY_REJECTED
                || treatmentSimulator.status() == SimulatorPlanOutcomeEvidence.Status.REPLAY_REJECTED
                || !baselineSimulator.worldModelFingerprint().equals(treatmentSimulator.worldModelFingerprint())
                || !baselineSimulator.evaluatorFingerprint().equals(treatmentSimulator.evaluatorFingerprint()))
            throw new SecurityException("independent simulator replay evidence is incomplete, rejected or unmatched");

        List<B6SimulatorLabelEvidence> labels = List.copyOf(Objects.requireNonNull(b6Labels, "b6Labels"));
        switch (mechanism) {
            case B1_MEMORY_LEARNING -> verifyB1((B1MemoryRunEvidence) baseline, (B1MemoryRunEvidence) treatment, bundle);
            case B2_RECOVERY_PROCEDURE_REUSE -> verifyB2((B2ProcedureRunEvidence) baseline, (B2ProcedureRunEvidence) treatment, bundle);
            case B3_COALITION_FEEDBACK -> verifyB3((B3CoalitionRunEvidence) baseline, (B3CoalitionRunEvidence) treatment, bundle);
            case B4_INTENTION_METHOD_AUTHORITY -> verifyB4((B4IntentionRunEvidence) baseline, (B4IntentionRunEvidence) treatment);
            case B6_UNCERTAINTY_CALIBRATION -> verifyB6((B6CalibrationRunEvidence) baseline, (B6CalibrationRunEvidence) treatment, bundle, labels,
                    graphPair.effectiveInputFingerprint());
        }
        if (mechanism != ExperimentMechanism.B6_UNCERTAINTY_CALIBRATION && !labels.isEmpty())
            throw new SecurityException("B6 labels cannot be attached to a non-B6 experiment pair");
        return MechanismSpecificExperimentPair.create(graphPair, baseline, treatment,
                baselineSimulator, treatmentSimulator, labels);
    }

    private static void verifyB1(B1MemoryRunEvidence baseline, B1MemoryRunEvidence treatment,
            MechanismTrainingArtifactBundle bundle) {
        if (!baseline.sourceEvidenceFingerprint().equals(bundle.b1().evidenceFingerprint())
                || !treatment.sourceEvidenceFingerprint().equals(bundle.b1().evidenceFingerprint())
                || !baseline.initialFeatureStateFingerprints().isEmpty())
            throw new SecurityException("B1 baseline or source-evidence provenance is invalid");
        Map<String, String> expectedSeeds = FullGraphMechanismEvidenceCollector.fingerprints(
                List.copyOf(bundle.b1().featureSeeds().values()));
        if (!treatment.initialFeatureStateFingerprints().equals(expectedSeeds))
            throw new SecurityException("B1 treatment did not start from the exact frozen TRAIN feature states");
        if (!treatment.finalFeatureStateFingerprints().keySet().containsAll(expectedSeeds.keySet()))
            throw new SecurityException("B1 treatment lost a TRAIN-derived feature reference");
    }

    private static void verifyB2(B2ProcedureRunEvidence baseline, B2ProcedureRunEvidence treatment,
            MechanismTrainingArtifactBundle bundle) {
        if (!baseline.procedures().isEmpty() || !baseline.suppliedReusableActionDescriptorIds().isEmpty()
                || !baseline.proposedReusableActionDescriptorIds().isEmpty()
                || baseline.externalActionDispatch() || treatment.externalActionDispatch())
            throw new SecurityException("B2 baseline/treatment crossed the procedure or dispatch boundary");
        var expected = new TreeMap<String, B2ProcedureRunEvidence.ProcedureObservation>();
        bundle.b2().proceduresByCondition().forEach((condition, source) -> {
            var artifact = source.artifact();
            expected.put(condition, new B2ProcedureRunEvidence.ProcedureObservation(condition,
                    artifact.sourceDraftId(), artifact.sourceFingerprint(), artifact.objectiveFingerprint(),
                    dev.supplychain.agent.memory.procedural.ProcedureStatus.ACTIVE.name(),
                    artifact.actions().stream().map(action -> action.actionId()).sorted().toList(),
                    artifact.evidence().stream().map(ref -> ref.evidenceType() + ":" + ref.evidenceId()).sorted().toList()));
        });
        var actual = new TreeMap<String, B2ProcedureRunEvidence.ProcedureObservation>();
        treatment.procedures().forEach(value -> {
            if (actual.putIfAbsent(value.conditionKey(), value) != null)
                throw new SecurityException("B2 treatment returned duplicate procedure condition evidence");
        });
        if (!actual.equals(expected)) throw new SecurityException("B2 treatment did not retrieve the exact TRAIN-approved procedure set");
        var expectedActionIds = expected.values().stream().flatMap(value -> value.actionDescriptorIds().stream())
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        if (!treatment.suppliedReusableActionDescriptorIds().equals(List.copyOf(expectedActionIds)))
            throw new SecurityException("B2 treatment planning request did not receive the exact registered reusable-action references");
        var actuallyProposedReusable = treatment.proposedActionDescriptorIds().stream()
                .filter(expectedActionIds::contains).toList();
        if (!treatment.proposedReusableActionDescriptorIds().equals(actuallyProposedReusable))
            throw new SecurityException("B2 reusable-action proposal observations do not match the selected planner candidate");
    }

    private static void verifyB3(B3CoalitionRunEvidence baseline, B3CoalitionRunEvidence treatment,
            MechanismTrainingArtifactBundle bundle) {
        if (!baseline.overlayCandidateFingerprint().isEmpty() || baseline.shadowOnly()
                || baseline.proposals().stream().anyMatch(proposal -> proposal.configuredAdjustment() != 0.0
                        || proposal.appliedAdjustment() != 0.0))
            throw new SecurityException("B3 baseline includes a learned coalition overlay");
        var candidate = bundle.b3().candidate();
        String expectedFingerprint = dev.supplychain.agent.mechanisms.b3.B3CoalitionFeedbackPolicyService.candidateFingerprint(candidate);
        if (candidate.disposition() != B3CoalitionFeedbackCandidate.Disposition.SHADOW
                || !treatment.shadowOnly() || !treatment.overlayCandidateFingerprint().equals(expectedFingerprint))
            throw new SecurityException("B3 treatment is not the exact frozen SHADOW candidate");
        double total = 0.0;
        for (var proposal : treatment.proposals()) {
            double expected = candidate.preferenceAdjustments().getOrDefault(proposal.preferenceKey(), 0.0);
            if (Double.compare(expected, proposal.configuredAdjustment()) != 0
                    || Double.compare(expected, proposal.appliedAdjustment()) != 0)
                throw new SecurityException("B3 treatment proposal adjustment differs from the frozen SHADOW preference value");
            if (proposal.selected()) total += expected;
        }
        if (Double.compare(total, treatment.totalAppliedAdjustment()) != 0)
            throw new SecurityException("B3 treatment aggregate does not equal the sum of selected coalition adjustments");
    }

    private static void verifyB4(B4IntentionRunEvidence baseline, B4IntentionRunEvidence treatment) {
        if (!baseline.objectiveFingerprint().equals(treatment.objectiveFingerprint())
                || !baseline.protectedConstraints().equals(treatment.protectedConstraints())
                || !baseline.initialMethodId().equals(treatment.initialMethodId())
                || !baseline.initialCandidatePlanReference().equals(treatment.initialCandidatePlanReference()))
            throw new SecurityException("B4 matched arms changed the committed objective, protected constraints or initial method");
        if (!baseline.finalMethodId().equals(baseline.initialMethodId()))
            throw new SecurityException("B4 fixed-method baseline changed its planning method");
        verifyTransitions(baseline);
        verifyTransitions(treatment);
    }

    private static void verifyTransitions(B4IntentionRunEvidence evidence) {
        long expectedSequence = 1;
        long expectedVersion = evidence.initialVersion();
        for (var transition : evidence.transitions()) {
            if (transition.sequence() != expectedSequence++ || transition.beforeVersion() != expectedVersion
                    || transition.actor().equals("unrecorded-actor")
                    || !transition.objectiveBefore().equals(evidence.objectiveFingerprint())
                    || !transition.objectiveAfter().equals(evidence.objectiveFingerprint()))
                throw new SecurityException("B4 committed transition audit is discontinuous, unattributed or mutates the objective");
            expectedVersion = transition.afterVersion();
        }
        if (expectedVersion != evidence.finalVersion())
            throw new SecurityException("B4 final version does not match the append-only transition audit");
    }

    private void verifyB6(B6CalibrationRunEvidence baseline, B6CalibrationRunEvidence treatment,
            MechanismTrainingArtifactBundle bundle, List<B6SimulatorLabelEvidence> labels, String unitFingerprint) {
        var profile = bundle.b6().profile();
        if (baseline.rawValues().isEmpty() || !baseline.rawValues().equals(baseline.observedValues())
                || baseline.exactShadowTracePresent() || !baseline.profileId().isEmpty())
            throw new SecurityException("B6 baseline did not preserve the raw uncalibrated assessment");
        if (!baseline.rawValues().equals(treatment.rawValues())
                || !treatment.exactShadowTracePresent()
                || !treatment.profileId().equals(profile.id())
                || treatment.profileVersion() != profile.version()
                || !treatment.targetId().equals(profile.targetId())
                || !"NOT_CALIBRATED".equals(treatment.calibrationStatus()))
            throw new SecurityException("B6 treatment does not carry the exact TRAINED shadow profile and matching raw prediction");
        for (UncertaintyDimension dimension : UncertaintyDimension.values()) {
            double expected = profile.calibrate(dimension, treatment.rawValues().get(dimension));
            if (Double.compare(expected, treatment.observedValues().get(dimension)) != 0)
                throw new SecurityException("B6 observed shadow value cannot be reproduced from the exact TRAINED profile: " + dimension);
        }
        if (labels.size() != UncertaintyDimension.values().length)
            throw new SecurityException("B6 HOLDOUT pair must contain exactly one independently verified label per dimension");
        var dimensions = new HashSet<UncertaintyDimension>();
        var samples = new HashSet<String>();
        var evidenceRefs = new HashSet<String>();
        String scope = profile.scopeId().value();
        for (B6SimulatorLabelEvidence label : labels) {
            var observation = label.observation();
            if (label.split() != ExperimentSplit.HOLDOUT || !label.experimentUnitFingerprint().equals(unitFingerprint)
                    || !observation.scopeId().value().equals(scope) || !observation.targetId().equals(profile.targetId())
                    || Double.compare(observation.predictedUncertainty(), baseline.rawValues().get(observation.dimension())) != 0
                    || !dimensions.add(observation.dimension()) || !samples.add(observation.sampleId())
                    || !evidenceRefs.add(observation.labelEvidence().evidenceType() + ":" + observation.labelEvidence().evidenceId())
                    || !b6HoldoutLabelVerifier.verify(label))
                throw new SecurityException("B6 HOLDOUT label failed exact input mapping, uniqueness or independent simulator verification");
        }
        if (!dimensions.equals(Set.of(UncertaintyDimension.values())))
            throw new SecurityException("B6 HOLDOUT labels do not cover every uncertainty dimension");
    }
}
