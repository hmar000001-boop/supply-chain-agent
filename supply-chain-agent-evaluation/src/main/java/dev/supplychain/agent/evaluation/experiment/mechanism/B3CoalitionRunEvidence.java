package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentArm;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import java.util.List;
import java.util.Objects;

/** B3 competition trace and the exact SHADOW adjustment values observed by workspace admission. */
public record B3CoalitionRunEvidence(
        ExperimentArm arm,
        String experimentUnitFingerprint,
        String trainingArtifactFingerprint,
        String workspaceSnapshotId,
        String workspaceFingerprint,
        String overlayCandidateFingerprint,
        boolean shadowOnly,
        List<ProposalObservation> proposals,
        int selectedCoalitionCount,
        double totalAppliedAdjustment,
        String evidenceFingerprint) implements MechanismSpecificExperimentEvidence {
    public record ProposalObservation(String preferenceKey, List<String> memberCandidateIds,
            boolean selected, double configuredAdjustment, double appliedAdjustment) {
        public ProposalObservation {
            preferenceKey = ExperimentEvidenceFingerprint.requireSha(preferenceKey, "preferenceKey");
            memberCandidateIds = List.copyOf(Objects.requireNonNull(memberCandidateIds, "memberCandidateIds"));
            if (memberCandidateIds.size() < 2 || !Double.isFinite(configuredAdjustment)
                    || !Double.isFinite(appliedAdjustment) || Math.abs(appliedAdjustment) > 5.0)
                throw new IllegalArgumentException("invalid B3 proposal observation");
        }
        String canonical() { return preferenceKey + "|" + memberCandidateIds + "|" + selected + "|"
                + Double.toHexString(configuredAdjustment) + "|" + Double.toHexString(appliedAdjustment); }
    }
    public B3CoalitionRunEvidence {
        arm = Objects.requireNonNull(arm, "arm");
        experimentUnitFingerprint = ExperimentEvidenceFingerprint.requireSha(experimentUnitFingerprint, "experimentUnitFingerprint");
        trainingArtifactFingerprint = ExperimentEvidenceFingerprint.requireSha(trainingArtifactFingerprint, "trainingArtifactFingerprint");
        workspaceSnapshotId = workspaceSnapshotId == null ? "" : workspaceSnapshotId.trim();
        workspaceFingerprint = workspaceFingerprint == null ? "" : workspaceFingerprint.trim();
        overlayCandidateFingerprint = overlayCandidateFingerprint == null ? "" : overlayCandidateFingerprint.trim();
        if (!workspaceFingerprint.isEmpty()) ExperimentEvidenceFingerprint.requireSha(workspaceFingerprint, "workspaceFingerprint");
        if (!overlayCandidateFingerprint.isEmpty()) ExperimentEvidenceFingerprint.requireSha(overlayCandidateFingerprint, "overlayCandidateFingerprint");
        proposals = List.copyOf(Objects.requireNonNull(proposals, "proposals"));
        if (selectedCoalitionCount < 0 || !Double.isFinite(totalAppliedAdjustment))
            throw new IllegalArgumentException("invalid B3 aggregate counters");
        if (arm == ExperimentArm.BASELINE && (!overlayCandidateFingerprint.isEmpty() || totalAppliedAdjustment != 0.0))
            throw new SecurityException("B3 baseline cannot carry a learned coalition overlay");
        if (arm == ExperimentArm.TREATMENT && !shadowOnly)
            throw new SecurityException("B3 learned preference must remain SHADOW-only in experimental graph");
        String expected = ExperimentEvidenceFingerprint.sha256(canonical(arm, experimentUnitFingerprint,
                trainingArtifactFingerprint, workspaceSnapshotId, workspaceFingerprint, overlayCandidateFingerprint,
                shadowOnly, proposals, selectedCoalitionCount, totalAppliedAdjustment));
        evidenceFingerprint = ExperimentEvidenceFingerprint.requireSha(evidenceFingerprint, "evidenceFingerprint");
        if (!evidenceFingerprint.equals(expected)) throw new SecurityException("B3 run evidence fingerprint mismatch");
    }
    @Override public ExperimentMechanism mechanism() { return ExperimentMechanism.B3_COALITION_FEEDBACK; }
    public static B3CoalitionRunEvidence create(ExperimentArm arm, String unit, String bundle, String workspaceId,
            String workspaceFp, String candidateFp, boolean shadowOnly, List<ProposalObservation> proposals,
            int coalitions, double adjustment) {
        return new B3CoalitionRunEvidence(arm, unit, bundle, workspaceId, workspaceFp, candidateFp,
                shadowOnly, proposals, coalitions, adjustment, ExperimentEvidenceFingerprint.sha256(canonical(
                arm, unit, bundle, workspaceId, workspaceFp, candidateFp, shadowOnly, proposals, coalitions, adjustment)));
    }
    @Override public String toJson() {
        String proposalJson = proposals.stream().map(p -> "{\"preferenceKey\":" + q(p.preferenceKey())
                + ",\"memberCandidateIds\":" + strings(p.memberCandidateIds()) + ",\"selected\":" + p.selected()
                + ",\"configuredAdjustment\":" + p.configuredAdjustment() + ",\"appliedAdjustment\":" + p.appliedAdjustment() + "}")
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"evidenceType\":\"B3_COALITION_SHADOW_TRACE\",\"arm\":" + q(arm.name())
                + ",\"experimentUnitFingerprint\":" + q(experimentUnitFingerprint)
                + ",\"trainingArtifactFingerprint\":" + q(trainingArtifactFingerprint)
                + ",\"workspaceSnapshotId\":" + q(workspaceSnapshotId) + ",\"workspaceFingerprint\":"
                + q(workspaceFingerprint) + ",\"overlayCandidateFingerprint\":" + q(overlayCandidateFingerprint)
                + ",\"shadowOnly\":" + shadowOnly + ",\"proposals\":[" + proposalJson
                + "],\"selectedCoalitionCount\":" + selectedCoalitionCount + ",\"totalAppliedAdjustment\":"
                + totalAppliedAdjustment + ",\"evidenceFingerprint\":" + q(evidenceFingerprint) + "}";
    }
    private static String canonical(ExperimentArm arm, String unit, String bundle, String workspaceId, String workspaceFp,
            String candidateFp, boolean shadow, List<ProposalObservation> proposals, int coalitions, double adjustment) {
        return arm + "|" + unit + "|" + bundle + "|" + workspaceId + "|" + workspaceFp + "|" + candidateFp
                + "|" + shadow + "|" + proposals.stream().map(ProposalObservation::canonical).sorted().toList()
                + "|" + coalitions + "|" + Double.toHexString(adjustment);
    }
    private static String strings(List<String> values) { return "[" + values.stream().map(B3CoalitionRunEvidence::q).collect(java.util.stream.Collectors.joining(",")) + "]"; }
    private static String q(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
