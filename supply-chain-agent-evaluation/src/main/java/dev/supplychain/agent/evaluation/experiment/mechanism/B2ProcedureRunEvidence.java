package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentArm;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import java.util.List;
import java.util.Objects;

/** Exact typed B2 planning knowledge retrieved by the real graph; this is never an execution grant. */
public record B2ProcedureRunEvidence(
        ExperimentArm arm,
        String experimentUnitFingerprint,
        String trainingArtifactFingerprint,
        List<ProcedureObservation> procedures,
        List<String> suppliedReusableActionDescriptorIds,
        List<String> proposedActionDescriptorIds,
        List<String> proposedReusableActionDescriptorIds,
        boolean externalActionDispatch,
        String evidenceFingerprint) implements MechanismSpecificExperimentEvidence {
    public record ProcedureObservation(String conditionKey, String sourceDraftId, String sourceFingerprint,
            String objectiveFingerprint, String procedureStatus, List<String> actionDescriptorIds,
            List<String> evidenceReferences) {
        public ProcedureObservation {
            conditionKey = ExperimentEvidenceFingerprint.required(conditionKey, "conditionKey");
            sourceDraftId = ExperimentEvidenceFingerprint.required(sourceDraftId, "sourceDraftId");
            sourceFingerprint = ExperimentEvidenceFingerprint.requireSha(sourceFingerprint, "sourceFingerprint");
            objectiveFingerprint = ExperimentEvidenceFingerprint.requireSha(objectiveFingerprint, "objectiveFingerprint");
            procedureStatus = ExperimentEvidenceFingerprint.required(procedureStatus, "procedureStatus");
            actionDescriptorIds = List.copyOf(Objects.requireNonNull(actionDescriptorIds, "actionDescriptorIds"));
            evidenceReferences = List.copyOf(Objects.requireNonNull(evidenceReferences, "evidenceReferences"));
            if (actionDescriptorIds.isEmpty() || evidenceReferences.isEmpty())
                throw new IllegalArgumentException("B2 procedure observation needs action descriptors and evidence references");
        }
        String canonical() { return conditionKey + "|" + sourceDraftId + "|" + sourceFingerprint + "|"
                + objectiveFingerprint + "|" + procedureStatus + "|" + actionDescriptorIds + "|" + evidenceReferences; }
    }
    public B2ProcedureRunEvidence {
        arm = Objects.requireNonNull(arm, "arm");
        experimentUnitFingerprint = ExperimentEvidenceFingerprint.requireSha(experimentUnitFingerprint, "experimentUnitFingerprint");
        trainingArtifactFingerprint = ExperimentEvidenceFingerprint.requireSha(trainingArtifactFingerprint, "trainingArtifactFingerprint");
        procedures = List.copyOf(Objects.requireNonNull(procedures, "procedures"));
        suppliedReusableActionDescriptorIds = stableIds(suppliedReusableActionDescriptorIds, "supplied reusable action descriptor");
        proposedActionDescriptorIds = stableIds(proposedActionDescriptorIds, "proposed action descriptor");
        proposedReusableActionDescriptorIds = stableIds(proposedReusableActionDescriptorIds, "proposed reusable action descriptor");
        if (!proposedActionDescriptorIds.containsAll(proposedReusableActionDescriptorIds)
                || !suppliedReusableActionDescriptorIds.containsAll(proposedReusableActionDescriptorIds))
            throw new SecurityException("B2 reused proposed actions must be both in the exact planning request and selected proposal");
        if (externalActionDispatch) throw new SecurityException("B2 procedure evidence cannot include an external action dispatch");
        if (arm == ExperimentArm.BASELINE && !procedures.isEmpty())
            throw new SecurityException("B2 baseline must not retrieve an active learned recovery procedure");
        String expected = ExperimentEvidenceFingerprint.sha256(canonical(arm, experimentUnitFingerprint,
                trainingArtifactFingerprint, procedures, suppliedReusableActionDescriptorIds,
                proposedActionDescriptorIds, proposedReusableActionDescriptorIds, externalActionDispatch));
        evidenceFingerprint = ExperimentEvidenceFingerprint.requireSha(evidenceFingerprint, "evidenceFingerprint");
        if (!evidenceFingerprint.equals(expected)) throw new SecurityException("B2 run evidence fingerprint mismatch");
    }
    @Override public ExperimentMechanism mechanism() { return ExperimentMechanism.B2_RECOVERY_PROCEDURE_REUSE; }
    public static B2ProcedureRunEvidence create(ExperimentArm arm, String unit, String bundle,
            List<ProcedureObservation> procedures, List<String> suppliedReusableActionDescriptors,
            List<String> proposedDescriptors, List<String> proposedReusableActionDescriptors) {
        return new B2ProcedureRunEvidence(arm, unit, bundle, procedures, suppliedReusableActionDescriptors,
                proposedDescriptors, proposedReusableActionDescriptors, false,
                ExperimentEvidenceFingerprint.sha256(canonical(arm, unit, bundle, procedures,
                        suppliedReusableActionDescriptors, proposedDescriptors, proposedReusableActionDescriptors, false)));
    }
    @Override public String toJson() {
        String procedureJson = procedures.stream().map(p -> "{\"conditionKey\":" + q(p.conditionKey())
                + ",\"sourceFingerprint\":" + q(p.sourceFingerprint()) + ",\"objectiveFingerprint\":" + q(p.objectiveFingerprint())
                + ",\"procedureStatus\":" + q(p.procedureStatus()) + ",\"actionDescriptorIds\":" + strings(p.actionDescriptorIds())
                + ",\"evidenceReferences\":" + strings(p.evidenceReferences()) + "}")
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"evidenceType\":\"B2_PROCEDURE_RETRIEVAL\",\"arm\":" + q(arm.name())
                + ",\"experimentUnitFingerprint\":" + q(experimentUnitFingerprint)
                + ",\"trainingArtifactFingerprint\":" + q(trainingArtifactFingerprint)
                + ",\"procedures\":[" + procedureJson + "],\"proposedActionDescriptorIds\":"
                + strings(proposedActionDescriptorIds) + ",\"suppliedReusableActionDescriptorIds\":"
                + strings(suppliedReusableActionDescriptorIds) + ",\"proposedReusableActionDescriptorIds\":"
                + strings(proposedReusableActionDescriptorIds) + ",\"externalActionDispatch\":false,\"evidenceFingerprint\":"
                + q(evidenceFingerprint) + "}";
    }
    private static String canonical(ExperimentArm arm, String unit, String bundle, List<ProcedureObservation> procedures,
            List<String> supplied, List<String> descriptors, List<String> reused, boolean dispatched) {
        return arm + "|" + unit + "|" + bundle + "|" + procedures.stream().map(ProcedureObservation::canonical).sorted().toList()
                + "|" + supplied.stream().sorted().toList() + "|" + descriptors.stream().sorted().toList()
                + "|" + reused.stream().sorted().toList() + "|" + dispatched;
    }
    private static List<String> stableIds(List<String> values, String name) {
        return Objects.requireNonNull(values, name).stream()
                .map(value -> ExperimentEvidenceFingerprint.required(value, name))
                .distinct().sorted().toList();
    }
    private static String strings(List<String> values) { return "[" + values.stream().map(B2ProcedureRunEvidence::q).collect(java.util.stream.Collectors.joining(",")) + "]"; }
    private static String q(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
