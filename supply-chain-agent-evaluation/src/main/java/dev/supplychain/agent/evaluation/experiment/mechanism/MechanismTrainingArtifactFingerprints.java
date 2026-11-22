package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateReference;
import dev.supplychain.agent.mechanisms.b1.B1MemoryFeatureState;
import dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeEvidence;
import dev.supplychain.agent.mechanisms.b3.WorkspaceCoalitionOutcomeEvidence;
import dev.supplychain.agent.mechanisms.b3.WorkspaceReceiptEvidence;
import dev.supplychain.agent.mechanisms.b3.WorkspaceCoalitionReceipt;
import dev.supplychain.agent.mechanisms.b2.ReusableRecoveryProcedure;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedureArtifact;
import dev.supplychain.agent.mechanisms.b6.UncertaintyCalibrationProfile;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Canonical content fingerprints for the frozen TRAIN artifacts; never hashes caller-supplied labels alone. */
public final class MechanismTrainingArtifactFingerprints {
    private MechanismTrainingArtifactFingerprints() { }

    public static String b1(B1MemoryTrainingArtifact artifact) {
        return b1(artifact.featureSeeds(), artifact.initialFeatureStates(), artifact.outcomes(),
                artifact.receiptsById(), artifact.trainingUnitFingerprints(), artifact.verifierId());
    }

    public static String b1(Map<B1MemoryCandidateReference, B1MemoryFeatureState> seeds,
            Map<B1MemoryCandidateReference, B1MemoryFeatureState> initial,
            List<DownstreamOutcomeEvidence> outcomes, Map<String, WorkspaceReceiptEvidence> receipts,
            Set<String> units, String verifierId) {
        String canonical = "B1|" + new TreeSet<>(units) + "|" + verifierId + "|"
                + orderedFeatureStates(initial) + "|" + orderedFeatureStates(seeds) + "|"
                + outcomes.stream().sorted(Comparator.comparing(DownstreamOutcomeEvidence::idempotencyKey))
                    .map(MechanismTrainingArtifactFingerprints::outcome).toList() + "|"
                + new TreeMap<>(receipts).values().stream().map(MechanismTrainingArtifactFingerprints::receipt).toList();
        return ExperimentEvidenceFingerprint.sha256(canonical);
    }

    public static String b2(B2ProcedureTrainingArtifact artifact) {
        return b2(artifact.proceduresByCondition(), artifact.sourceUnitsByCondition());
    }

    public static String b2(Map<String, ReusableRecoveryProcedure> procedures, Map<String, Set<String>> units) {
        var rows = new TreeMap<String, String>();
        procedures.forEach((condition, procedure) -> rows.put(condition,
                procedure.procedure().toString() + "|" + procedureArtifact(procedure.artifact())));
        return ExperimentEvidenceFingerprint.sha256("B2|" + rows + "|" + sortedSetMap(units));
    }

    public static String b3(B3CoalitionTrainingArtifact artifact) {
        return b3(artifact.candidate(), artifact.outcomes(), artifact.receiptsById());
    }

    public static String b3(dev.supplychain.agent.mechanisms.b3.B3CoalitionFeedbackCandidate candidate,
            List<WorkspaceCoalitionOutcomeEvidence> outcomes, Map<String, WorkspaceReceiptEvidence> receipts) {
        String candidateCanonical = candidate.policyId() + "|" + candidate.version() + "|" + candidate.scopeId().value()
                + "|" + new TreeMap<>(candidate.preferenceAdjustments()) + "|" + candidate.sourceOutcomeIds()
                + "|" + new TreeSet<>(candidate.trainingUnitFingerprints()) + "|" + candidate.minimumObservationsPerCoalition()
                + "|" + Double.toHexString(candidate.maximumAbsoluteAdjustment()) + "|" + candidate.sourceFingerprint()
                + "|" + candidate.trainedAt() + "|" + candidate.disposition();
        String outcomeRows = outcomes.stream().sorted(Comparator.comparing(WorkspaceCoalitionOutcomeEvidence::idempotencyKey))
                .map(MechanismTrainingArtifactFingerprints::coalitionOutcome).toList().toString();
        String receiptRows = new TreeMap<>(receipts).values().stream()
                .map(MechanismTrainingArtifactFingerprints::receipt).toList().toString();
        return ExperimentEvidenceFingerprint.sha256("B3|" + candidateCanonical + "|" + outcomeRows + "|" + receiptRows);
    }

    public static String b6(B6CalibrationTrainingArtifact artifact) {
        return b6(artifact.profile(), artifact.labels(), artifact.trainingUnitFingerprints(), artifact.labelVerifierId());
    }

    public static String b6(UncertaintyCalibrationProfile profile, List<B6SimulatorLabelEvidence> labels,
            Set<String> units, String verifierId) {
        String bins = profile.bins().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue().stream()
                        .sorted(Comparator.comparingInt(dev.supplychain.agent.mechanisms.b6.UncertaintyCalibrationBin::index))
                        .map(bin -> bin.index() + ":" + bin.sampleCount() + ":" + Double.toHexString(bin.meanPredictedUncertainty())
                                + ":" + bin.realizedErrors() + ":" + Double.toHexString(bin.calibratedUncertainty())).toList())
                .toList().toString();
        String profileCanonical = profile.id() + "|" + profile.scopeId().value() + "|" + profile.targetId() + "|"
                + profile.version() + "|" + bins + "|" + profile.trainingDatasetFingerprint() + "|" + profile.validationDatasetFingerprint() + "|"
                + profile.trainingSampleCount() + "|" + profile.validationSampleCount() + "|"
                + Double.toHexString(profile.validationExpectedCalibrationError()) + "|" + profile.trainedBy() + "|"
                + profile.reviewedBy() + "|" + profile.validatedAt() + "|" + profile.status() + "|"
                + profile.createdAt() + "|" + profile.activatedAt() + "|" + profile.activatedBy() + "|" + profile.trainingSampleIdHashes().stream().sorted().toList() + "|"
                + profile.trainingEvidenceHashes().stream().sorted().toList() + "|"
                + profile.validationSampleIdHashes().stream().sorted().toList() + "|"
                + profile.validationEvidenceHashes().stream().sorted().toList();
        String labelRows = labels.stream().sorted(Comparator.comparing(value -> value.observation().sampleId()))
                .map(value -> value.split() + "|" + value.experimentUnitFingerprint() + "|" + value.simulatorRunFingerprint()
                        + "|" + value.simulatorStateFingerprint() + "|" + observation(value.observation())).toList().toString();
        return ExperimentEvidenceFingerprint.sha256("B6|" + new TreeSet<>(units) + "|" + verifierId + "|"
                + profileCanonical + "|" + labelRows);
    }

    private static String orderedFeatureStates(Map<B1MemoryCandidateReference, B1MemoryFeatureState> states) {
        return states.entrySet().stream().sorted(Comparator.comparing(entry -> reference(entry.getKey())))
                .map(entry -> featureState(entry.getValue())).toList().toString();
    }

    private static String reference(B1MemoryCandidateReference ref) {
        return ref.kind() + "|" + ref.scopeId().value() + "|" + ref.itemId() + "|" + ref.itemVersion();
    }

    private static String featureState(B1MemoryFeatureState state) {
        return reference(state.reference()) + "|" + state.accessHistory() + "|" + state.verifiedUseHistory() + "|"
                + new TreeMap<>(state.cueAssociationStrengths()).entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + Double.toHexString(entry.getValue())).toList()
                + "|" + Double.toHexString(state.expectedDownstreamUtility()) + "|" + state.usefulOutcomes()
                + "|" + state.harmfulOutcomes() + "|" + state.neutralOutcomes() + "|"
                + new TreeSet<>(state.appliedOutcomeKeys()) + "|" + new TreeSet<>(state.appliedAccessEventKeys())
                + "|" + state.revision() + "|" + state.updatedAt();
    }

    private static String outcome(DownstreamOutcomeEvidence value) {
        return value.idempotencyKey() + "|" + value.receiptId() + "|" + value.scopeId().value() + "|"
                + value.cycleId() + "|" + value.workspaceId() + "|" + value.candidateId() + "|"
                + value.candidateFingerprint() + "|" + value.consumerId() + "|" + value.signal() + "|"
                + value.verifierId() + "|" + value.evidence().stream().map(e -> e.evidenceType() + ":" + e.evidenceId()).sorted().toList()
                + "|" + value.experimentUnitFingerprint() + "|" + value.admissionPolicyVersion() + "|" + value.verifiedAt()
                + "|" + value.memoryAttributions().stream().map(a -> reference(a.reference()) + ":"
                    + new TreeSet<>(a.queryCues()) + ":" + a.sourceCandidateFingerprint()).sorted().toList();
    }

    private static String receipt(WorkspaceReceiptEvidence value) {
        String attributions = new TreeMap<>(value.memoryAttributionsByCandidate()).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().stream()
                        .map(a -> reference(a.reference()) + ":" + new TreeSet<>(a.queryCues()) + ":" + a.sourceCandidateFingerprint())
                        .sorted().toList()).toList().toString();
        String coalitions = new TreeMap<>(value.selectedCoalitions()).values().stream()
                .map(MechanismTrainingArtifactFingerprints::coalitionReceipt).toList().toString();
        return value.receiptId() + "|" + value.scopeId().value() + "|" + value.cycleId() + "|" + value.workspaceId()
                + "|" + value.consumerId() + "|" + value.status() + "|" + new TreeMap<>(value.consumedCandidateFingerprints())
                + "|" + value.workspaceFingerprint() + "|" + value.experimentUnitFingerprint() + "|"
                + value.admissionPolicyVersion() + "|" + value.receivedAt() + "|" + attributions + "|" + coalitions
                + "|" + value.uncertaintyDirective();
    }

    private static String coalitionReceipt(WorkspaceCoalitionReceipt value) {
        return value.coalitionId() + "|" + value.scopeId().value() + "|" + value.workspaceId() + "|" + value.round()
                + "|" + value.proposalFingerprint() + "|" + value.preferenceKey() + "|" + new TreeSet<>(value.candidateIds())
                + "|" + new TreeMap<>(value.candidateFingerprints()) + "|" + new TreeSet<>(value.jointCoverageTags())
                + "|" + value.contextUnits() + "|" + value.experimentUnitFingerprint();
    }

    private static String coalitionOutcome(WorkspaceCoalitionOutcomeEvidence value) {
        return value.idempotencyKey() + "|" + value.receiptId() + "|" + value.scopeId().value() + "|" + value.workspaceId()
                + "|" + value.cycleId() + "|" + value.consumerId() + "|" + value.coalitionId() + "|"
                + value.proposalFingerprint() + "|" + value.preferenceKey() + "|" + new TreeSet<>(value.candidateIds())
                + "|" + value.signal() + "|" + value.verifierId() + "|"
                + value.evidence().stream().map(e -> e.evidenceType() + ":" + e.evidenceId()).sorted().toList()
                + "|" + value.experimentUnitFingerprint() + "|" + value.admissionPolicyVersion() + "|" + value.verifiedAt();
    }

    private static String procedureArtifact(RecoveryProcedureArtifact value) {
        return value.formatVersion() + "|" + value.sourceDraftId() + "|" + value.scopeId().value() + "|"
                + value.conditionKey() + "|" + value.objectiveFingerprint() + "|"
                + value.actions().stream().map(action -> action.actionId() + ":" + action.requiredCapability() + ":"
                        + action.effect() + ":" + action.descriptorFingerprint()).toList() + "|"
                + value.preconditionKeys() + "|" + value.expectedPostconditionKeys() + "|" + value.failureConditionKeys() + "|"
                + value.evidence().stream().map(e -> e.evidenceType() + ":" + e.evidenceId()).sorted().toList() + "|"
                + value.sourceFingerprint() + "|" + value.approvedBy() + "|" + value.approvalReference() + "|" + value.approvedAt();
    }

    private static String observation(dev.supplychain.agent.mechanisms.b6.UncertaintyCalibrationObservation value) {
        return value.sampleId() + "|" + value.scopeId().value() + "|" + value.targetId() + "|" + value.dimension()
                + "|" + Double.toHexString(value.predictedUncertainty()) + "|" + value.realizedError() + "|"
                + value.labelEvidence().evidenceType() + ":" + value.labelEvidence().evidenceId() + "|" + value.observedAt();
    }

    private static String sortedSetMap(Map<String, Set<String>> values) {
        var ordered = new TreeMap<String, List<String>>();
        values.forEach((key, set) -> ordered.put(key, set.stream().sorted().toList()));
        return ordered.toString();
    }
}
