package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import dev.supplychain.agent.evaluation.experiment.scenario.M8ExperimentScenarioFactory;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateReference;
import dev.supplychain.agent.mechanisms.b1.B1MemoryFeatureState;
import dev.supplychain.agent.mechanisms.b2.ReusableRecoveryProcedure;
import dev.supplychain.agent.mechanisms.b3.B3CoalitionFeedbackPolicyService;
import dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeEvidence;
import dev.supplychain.agent.mechanisms.b3.VerifiedWorkspaceCoalitionOutcomeEvidence;
import dev.supplychain.agent.mechanisms.b3.WorkspaceCoalitionOutcomeEvidence;
import dev.supplychain.agent.mechanisms.b3.WorkspaceReceiptEvidence;
import dev.supplychain.agent.mechanisms.b6.UncertaintyCalibrationObservation;
import dev.supplychain.agent.mechanisms.b6.UncertaintyCalibrationTrainer;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Constructs the one admissible TRAIN artifact bundle from typed source records and explicit
 * independent verifiers. It never reads HOLDOUT/TRANSFER evidence and never validates or activates
 * B3/B6 policies.
 */
public final class MechanismTrainingArtifactBuilder {
    private final MechanismTrainingArtifactVerificationServices verificationServices;
    private final MechanismTrainingArtifactVerifier artifactVerifier;
    private final B3CoalitionFeedbackPolicyService b3PolicyService;
    private final UncertaintyCalibrationTrainer b6Trainer;

    public MechanismTrainingArtifactBuilder(MechanismTrainingArtifactVerificationServices verificationServices,
            B3CoalitionFeedbackPolicyService b3PolicyService, UncertaintyCalibrationTrainer b6Trainer) {
        this.verificationServices = Objects.requireNonNull(verificationServices, "verificationServices");
        this.artifactVerifier = new MechanismTrainingArtifactVerifier(verificationServices);
        this.b3PolicyService = Objects.requireNonNull(b3PolicyService, "b3PolicyService");
        this.b6Trainer = Objects.requireNonNull(b6Trainer, "b6Trainer");
    }

    public MechanismTrainingArtifactBundle build(String candidateCodeFingerprint, Inputs inputs, Instant frozenAt) {
        String candidate = ExperimentEvidenceFingerprint.requireSha(candidateCodeFingerprint, "candidateCodeFingerprint");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(frozenAt, "frozenAt");
        Set<String> trainUnits = MechanismTrainingArtifactBundle.exactTrainUnitFingerprints();
        MemoryScopeId researchScope = new MemoryScopeId(M8ExperimentScenarioFactory.EXPERIMENT_MEMORY_SCOPE_ID);

        B1MemoryTrainingArtifact b1 = buildB1(inputs.b1(), trainUnits, researchScope);
        B2ProcedureTrainingArtifact b2 = buildB2(inputs.b2(), trainUnits, researchScope);
        B3CoalitionTrainingArtifact b3 = buildB3(inputs.b3(), trainUnits, researchScope, frozenAt);
        B6CalibrationTrainingArtifact b6 = buildB6(inputs.b6(), trainUnits, researchScope, frozenAt);

        MechanismTrainingArtifactBundle bundle = MechanismTrainingArtifactBundle.freeze(
                candidate, trainUnits, b1, b2, b3, b6, frozenAt);
        // A bundle is not returned until each source record and derived mechanism artifact has been
        // independently revalidated from its underlying content and provenance.
        artifactVerifier.verify(bundle);
        return bundle;
    }

    private B1MemoryTrainingArtifact buildB1(B1Input input, Set<String> trainUnits, MemoryScopeId scope) {
        Map<B1MemoryCandidateReference, B1MemoryFeatureState> seeds = input.featureSeeds();
        Map<B1MemoryCandidateReference, B1MemoryFeatureState> initial = input.initialFeatureStates();
        if (!seeds.keySet().equals(initial.keySet()) || seeds.isEmpty())
            throw new SecurityException("B1 initial and final feature snapshots must cover the exact same nonempty reference set");
        if (seeds.keySet().stream().anyMatch(ref -> !ref.scopeId().equals(scope)))
            throw new SecurityException("B1 features must use the synthetic shared research scope");
        Set<String> sourceUnits = input.outcomes().stream().map(DownstreamOutcomeEvidence::experimentUnitFingerprint)
                .collect(Collectors.toUnmodifiableSet());
        requireTrainSubset(sourceUnits, trainUnits, "B1 outcomes");
        if (sourceUnits.size() < 2) throw new SecurityException("B1 training requires outcomes from at least two distinct TRAIN units");
        String fingerprint = MechanismTrainingArtifactFingerprints.b1(seeds, initial, input.outcomes(),
                input.receiptsById(), sourceUnits, input.verifierId());
        return new B1MemoryTrainingArtifact(seeds, initial, input.outcomes(), input.receiptsById(),
                sourceUnits, input.verifierId(), fingerprint);
    }

    private B2ProcedureTrainingArtifact buildB2(B2Input input, Set<String> trainUnits, MemoryScopeId scope) {
        if (input.proceduresByCondition().isEmpty() || input.sourceUnitsByCondition().isEmpty())
            throw new SecurityException("B2 treatment requires at least one approved reusable TRAIN procedure");
        if (!input.proceduresByCondition().keySet().equals(input.sourceUnitsByCondition().keySet()))
            throw new SecurityException("B2 procedure and source-unit condition sets must match exactly");
        input.proceduresByCondition().values().forEach(procedure -> {
            if (!procedure.procedure().scopeId().equals(scope) || !procedure.artifact().scopeId().equals(scope))
                throw new SecurityException("B2 procedure artifact crosses the synthetic research scope");
        });
        input.sourceUnitsByCondition().forEach((condition, units) -> {
            requireTrainSubset(units, trainUnits, "B2 procedure " + condition);
            if (units.size() < 2) throw new SecurityException("B2 procedure requires at least two distinct TRAIN source units");
        });
        String fingerprint = MechanismTrainingArtifactFingerprints.b2(input.proceduresByCondition(), input.sourceUnitsByCondition());
        return new B2ProcedureTrainingArtifact(input.proceduresByCondition(), input.sourceUnitsByCondition(), fingerprint);
    }

    private B3CoalitionTrainingArtifact buildB3(B3Input input, Set<String> trainUnits,
            MemoryScopeId scope, Instant frozenAt) {
        List<VerifiedWorkspaceCoalitionOutcomeEvidence> verified = input.verifiedOutcomes();
        if (verified.isEmpty()) throw new SecurityException("B3 cannot be trained without verifier-minted coalition outcome capabilities");
        Set<String> sourceUnits = verified.stream().map(VerifiedWorkspaceCoalitionOutcomeEvidence::experimentUnitFingerprint)
                .collect(Collectors.toUnmodifiableSet());
        requireTrainSubset(sourceUnits, trainUnits, "B3 coalition outcomes");
        if (sourceUnits.size() < 2) throw new SecurityException("B3 learning requires at least two distinct TRAIN units");
        if (input.trainedAt().isAfter(frozenAt)) throw new SecurityException("B3 training timestamp is after the frozen artifact boundary");
        List<WorkspaceCoalitionOutcomeEvidence> outcomes = verified.stream()
                .map(VerifiedWorkspaceCoalitionOutcomeEvidence::evidence).toList();
        if (outcomes.stream().anyMatch(value -> !value.scopeId().equals(scope)))
            throw new SecurityException("B3 outcome ledger crosses the synthetic research scope");
        var candidate = b3PolicyService.train(input.policyId(), input.version(), verified,
                input.minimumObservationsPerCoalition(), input.maximumAbsoluteAdjustment(), input.trainedAt());
        if (candidate.disposition() != dev.supplychain.agent.mechanisms.b3.B3CoalitionFeedbackCandidate.Disposition.SHADOW)
            throw new SecurityException("B3 trainer returned a non-SHADOW candidate");
        String fingerprint = MechanismTrainingArtifactFingerprints.b3(candidate, outcomes, input.receiptsById());
        return new B3CoalitionTrainingArtifact(candidate, outcomes, input.receiptsById(), fingerprint);
    }

    private B6CalibrationTrainingArtifact buildB6(B6Input input, Set<String> trainUnits,
            MemoryScopeId scope, Instant frozenAt) {
        List<B6SimulatorLabelEvidence> labels = input.labels();
        if (labels.isEmpty()) throw new SecurityException("B6 requires independently produced simulator label evidence");
        if (labels.stream().anyMatch(label -> label.split() != ExperimentSplit.TRAIN
                || !label.observation().scopeId().equals(scope)))
            throw new SecurityException("B6 labels must be TRAIN-only and use the synthetic shared research scope");
        Set<String> sourceUnits = labels.stream().map(B6SimulatorLabelEvidence::experimentUnitFingerprint)
                .collect(Collectors.toUnmodifiableSet());
        requireTrainSubset(sourceUnits, trainUnits, "B6 labels");
        if (sourceUnits.size() < 100)
            throw new SecurityException("B6 calibration requires labels from at least 100 distinct TRAIN units");
        String target = labels.getFirst().observation().targetId();
        if (labels.stream().anyMatch(label -> !label.observation().targetId().equals(target)))
            throw new SecurityException("B6 label set must target one exact uncertainty task");
        Instant latest = labels.stream().map(label -> label.observation().observedAt()).max(Instant::compareTo).orElseThrow();
        if (latest.isAfter(frozenAt)) throw new SecurityException("B6 label timestamp is after the frozen artifact boundary");
        List<UncertaintyCalibrationObservation> observations = labels.stream()
                .map(B6SimulatorLabelEvidence::observation).toList();
        var profile = b6Trainer.train(scope, target, observations, input.trainerId(), frozenAt);
        if (profile.status() != dev.supplychain.agent.mechanisms.b6.UncertaintyCalibrationProfile.Status.TRAINED)
            throw new SecurityException("B6 trainer returned a validated or activated profile");
        String fingerprint = MechanismTrainingArtifactFingerprints.b6(profile, labels, sourceUnits, input.labelVerifierId());
        return new B6CalibrationTrainingArtifact(profile, labels, sourceUnits, input.labelVerifierId(), fingerprint);
    }

    private static void requireTrainSubset(Set<String> source, Set<String> train, String name) {
        if (source.isEmpty() || !train.containsAll(source))
            throw new SecurityException(name + " contains no evidence or a unit outside the preregistered TRAIN split");
    }

    public record Inputs(B1Input b1, B2Input b2, B3Input b3, B6Input b6) {
        public Inputs {
            b1 = Objects.requireNonNull(b1, "b1");
            b2 = Objects.requireNonNull(b2, "b2");
            b3 = Objects.requireNonNull(b3, "b3");
            b6 = Objects.requireNonNull(b6, "b6");
        }
    }

    public record B1Input(Map<B1MemoryCandidateReference, B1MemoryFeatureState> featureSeeds,
            Map<B1MemoryCandidateReference, B1MemoryFeatureState> initialFeatureStates,
            List<DownstreamOutcomeEvidence> outcomes, Map<String, WorkspaceReceiptEvidence> receiptsById,
            String verifierId) {
        public B1Input {
            featureSeeds = orderedReferences(Objects.requireNonNull(featureSeeds, "featureSeeds"));
            initialFeatureStates = orderedReferences(Objects.requireNonNull(initialFeatureStates, "initialFeatureStates"));
            outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
            receiptsById = Map.copyOf(new TreeMap<>(Objects.requireNonNull(receiptsById, "receiptsById")));
            verifierId = ExperimentEvidenceFingerprint.required(verifierId, "verifierId");
        }
    }

    private static Map<B1MemoryCandidateReference, B1MemoryFeatureState> orderedReferences(
            Map<B1MemoryCandidateReference, B1MemoryFeatureState> source) {
        var ordered = new java.util.LinkedHashMap<B1MemoryCandidateReference, B1MemoryFeatureState>();
        source.entrySet().stream().sorted(java.util.Comparator.comparing(entry -> entry.getKey().candidateId()
                + "|" + entry.getKey().scopeId().value() + "|" + entry.getKey().kind()))
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return java.util.Collections.unmodifiableMap(ordered);
    }

    public record B2Input(Map<String, ReusableRecoveryProcedure> proceduresByCondition,
            Map<String, Set<String>> sourceUnitsByCondition) {
        public B2Input {
            proceduresByCondition = Map.copyOf(new TreeMap<>(Objects.requireNonNull(proceduresByCondition, "proceduresByCondition")));
            var units = new TreeMap<String, Set<String>>();
            Objects.requireNonNull(sourceUnitsByCondition, "sourceUnitsByCondition").forEach((key, values) ->
                    units.put(key, Set.copyOf(values)));
            sourceUnitsByCondition = Map.copyOf(units);
        }
    }

    public record B3Input(List<VerifiedWorkspaceCoalitionOutcomeEvidence> verifiedOutcomes,
            Map<String, WorkspaceReceiptEvidence> receiptsById, String policyId, long version,
            int minimumObservationsPerCoalition, double maximumAbsoluteAdjustment, Instant trainedAt) {
        public B3Input {
            verifiedOutcomes = List.copyOf(Objects.requireNonNull(verifiedOutcomes, "verifiedOutcomes"));
            receiptsById = Map.copyOf(new TreeMap<>(Objects.requireNonNull(receiptsById, "receiptsById")));
            policyId = ExperimentEvidenceFingerprint.required(policyId, "policyId");
            if (version < 1 || minimumObservationsPerCoalition < 1 || !Double.isFinite(maximumAbsoluteAdjustment)
                    || maximumAbsoluteAdjustment <= 0 || maximumAbsoluteAdjustment > 5)
                throw new IllegalArgumentException("invalid B3 training parameters");
            trainedAt = Objects.requireNonNull(trainedAt, "trainedAt");
        }
    }

    public record B6Input(List<B6SimulatorLabelEvidence> labels, String trainerId, String labelVerifierId) {
        public B6Input {
            labels = List.copyOf(Objects.requireNonNull(labels, "labels"));
            trainerId = ExperimentEvidenceFingerprint.required(trainerId, "trainerId");
            labelVerifierId = ExperimentEvidenceFingerprint.required(labelVerifierId, "labelVerifierId");
        }
    }
}
