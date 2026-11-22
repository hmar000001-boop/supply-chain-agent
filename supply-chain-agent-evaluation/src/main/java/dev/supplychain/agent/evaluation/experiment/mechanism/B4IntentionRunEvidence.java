package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentArm;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import dev.supplychain.agent.mechanisms.b4.InMemoryRecoveryIntentionTransitionRecorder;
import java.util.List;
import java.util.Objects;

/** Full-graph B4 intention snapshot and append-only committed transition audit. */
public record B4IntentionRunEvidence(
        ExperimentArm arm,
        String experimentUnitFingerprint,
        String trainingArtifactFingerprint,
        String intentionId,
        String objectiveFingerprint,
        List<String> protectedConstraints,
        String initialMethodId,
        String initialCandidatePlanReference,
        String finalMethodId,
        String finalCandidatePlanReference,
        long initialVersion,
        long finalVersion,
        List<TransitionObservation> transitions,
        String evidenceFingerprint) implements MechanismSpecificExperimentEvidence {
    public record TransitionObservation(long sequence, long beforeVersion, long afterVersion,
            String kind, String beforeMethodId, String afterMethodId, String objectiveBefore,
            String objectiveAfter, String actor, String beforeStatus, String afterStatus) {
        public TransitionObservation {
            if (sequence < 1 || beforeVersion < 0 || afterVersion != beforeVersion + 1)
                throw new IllegalArgumentException("B4 transition must be a contiguous committed version increment");
            kind = ExperimentEvidenceFingerprint.required(kind, "kind");
            beforeMethodId = ExperimentEvidenceFingerprint.required(beforeMethodId, "beforeMethodId");
            afterMethodId = ExperimentEvidenceFingerprint.required(afterMethodId, "afterMethodId");
            objectiveBefore = ExperimentEvidenceFingerprint.requireSha(objectiveBefore, "objectiveBefore");
            objectiveAfter = ExperimentEvidenceFingerprint.requireSha(objectiveAfter, "objectiveAfter");
            actor = ExperimentEvidenceFingerprint.required(actor, "actor");
            beforeStatus = ExperimentEvidenceFingerprint.required(beforeStatus, "beforeStatus");
            afterStatus = ExperimentEvidenceFingerprint.required(afterStatus, "afterStatus");
            if (!objectiveBefore.equals(objectiveAfter)) throw new SecurityException("B4 transition changed the committed objective");
        }
        String canonical() { return sequence + "|" + beforeVersion + "|" + afterVersion + "|" + kind + "|"
                + beforeMethodId + "|" + afterMethodId + "|" + objectiveBefore + "|" + objectiveAfter + "|"
                + actor + "|" + beforeStatus + "|" + afterStatus; }
    }
    public B4IntentionRunEvidence {
        arm = Objects.requireNonNull(arm, "arm");
        experimentUnitFingerprint = ExperimentEvidenceFingerprint.requireSha(experimentUnitFingerprint, "experimentUnitFingerprint");
        trainingArtifactFingerprint = ExperimentEvidenceFingerprint.requireSha(trainingArtifactFingerprint, "trainingArtifactFingerprint");
        intentionId = intentionId == null ? "" : intentionId.trim();
        objectiveFingerprint = objectiveFingerprint == null ? "" : objectiveFingerprint.trim();
        if (!objectiveFingerprint.isEmpty()) ExperimentEvidenceFingerprint.requireSha(objectiveFingerprint, "objectiveFingerprint");
        protectedConstraints = Objects.requireNonNull(protectedConstraints, "protectedConstraints").stream().sorted().toList();
        initialMethodId = initialMethodId == null ? "" : initialMethodId.trim();
        initialCandidatePlanReference = initialCandidatePlanReference == null ? "" : initialCandidatePlanReference.trim();
        finalMethodId = finalMethodId == null ? "" : finalMethodId.trim();
        finalCandidatePlanReference = finalCandidatePlanReference == null ? "" : finalCandidatePlanReference.trim();
        if (initialVersion < 0 || finalVersion < 0) throw new IllegalArgumentException("B4 intention version cannot be negative");
        transitions = List.copyOf(Objects.requireNonNull(transitions, "transitions"));
        if (transitions.stream().map(TransitionObservation::sequence).distinct().count() != transitions.size())
            throw new SecurityException("B4 transition audit contains duplicate sequence numbers");
        String committedObjectiveFingerprint = objectiveFingerprint;
        if (!committedObjectiveFingerprint.isEmpty()
                && transitions.stream().anyMatch(t -> !committedObjectiveFingerprint.equals(t.objectiveBefore())))
            throw new SecurityException("B4 audit objective does not match the run's committed objective");
        String expected = ExperimentEvidenceFingerprint.sha256(canonical(arm, experimentUnitFingerprint,
                trainingArtifactFingerprint, intentionId, objectiveFingerprint, protectedConstraints,
                initialMethodId, initialCandidatePlanReference, finalMethodId, finalCandidatePlanReference,
                initialVersion, finalVersion, transitions));
        evidenceFingerprint = ExperimentEvidenceFingerprint.requireSha(evidenceFingerprint, "evidenceFingerprint");
        if (!evidenceFingerprint.equals(expected)) throw new SecurityException("B4 run evidence fingerprint mismatch");
    }
    @Override public ExperimentMechanism mechanism() { return ExperimentMechanism.B4_INTENTION_METHOD_AUTHORITY; }
    public static B4IntentionRunEvidence create(ExperimentArm arm, String unit, String bundle, String intentionId,
            String objective, List<String> constraints, String initialMethod, String initialPlan,
            String finalMethod, String finalPlan, long initialVersion, long finalVersion,
            List<TransitionObservation> transitions) {
        return new B4IntentionRunEvidence(arm, unit, bundle, intentionId, objective, constraints,
                initialMethod, initialPlan, finalMethod, finalPlan, initialVersion, finalVersion,
                transitions, ExperimentEvidenceFingerprint.sha256(canonical(arm, unit, bundle, intentionId,
                objective, constraints, initialMethod, initialPlan, finalMethod, finalPlan, initialVersion,
                finalVersion, transitions)));
    }
    @Override public String toJson() {
        return "{\"evidenceType\":\"B4_INTENTION_TRANSITIONS\",\"arm\":" + q(arm.name())
                + ",\"experimentUnitFingerprint\":" + q(experimentUnitFingerprint)
                + ",\"trainingArtifactFingerprint\":" + q(trainingArtifactFingerprint)
                + ",\"intentionId\":" + q(intentionId) + ",\"objectiveFingerprint\":" + q(objectiveFingerprint)
                + ",\"protectedConstraints\":" + strings(protectedConstraints)
                + ",\"initialMethodId\":" + q(initialMethodId) + ",\"initialCandidatePlanReference\":" + q(initialCandidatePlanReference)
                + ",\"finalMethodId\":" + q(finalMethodId) + ",\"finalCandidatePlanReference\":" + q(finalCandidatePlanReference)
                + ",\"initialVersion\":" + initialVersion + ",\"finalVersion\":" + finalVersion
                + ",\"transitions\":" + transitions.size() + ",\"evidenceFingerprint\":" + q(evidenceFingerprint) + "}";
    }
    public static List<TransitionObservation> observations(List<InMemoryRecoveryIntentionTransitionRecorder.Entry> entries) {
        return entries.stream().map(entry -> {
            return new TransitionObservation(entry.sequence(), entry.before().version(), entry.after().version(),
                    entry.kind().name(), entry.before().currentMethod().methodId(), entry.after().currentMethod().methodId(),
                    entry.before().objectiveFingerprint(), entry.after().objectiveFingerprint(), entry.actor(),
                    entry.before().status().name(), entry.after().status().name());
        }).toList();
    }
    private static String canonical(ExperimentArm arm, String unit, String bundle, String intention, String objective,
            List<String> constraints, String initialMethod, String initialPlan, String finalMethod, String finalPlan,
            long initialVersion, long finalVersion, List<TransitionObservation> transitions) {
        return arm + "|" + unit + "|" + bundle + "|" + intention + "|" + objective + "|" + constraints.stream().sorted().toList()
                + "|" + initialMethod + "|" + initialPlan + "|" + finalMethod + "|" + finalPlan + "|"
                + initialVersion + "|" + finalVersion + "|" + transitions.stream().map(TransitionObservation::canonical).toList();
    }
    private static String strings(List<String> values) { return "[" + values.stream().map(B4IntentionRunEvidence::q).collect(java.util.stream.Collectors.joining(",")) + "]"; }
    private static String q(String value) { return "\"" + Objects.requireNonNullElse(value, "").replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
