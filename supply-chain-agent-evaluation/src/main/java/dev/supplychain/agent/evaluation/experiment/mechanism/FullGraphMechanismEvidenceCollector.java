package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentArm;
import dev.supplychain.agent.evaluation.experiment.execution.FullGraphExperimentSession;
import dev.supplychain.agent.mechanisms.b1.B1MemoryFeatureState;
import dev.supplychain.agent.mechanisms.b2.ReusableRecoveryProcedure;
import dev.supplychain.agent.mechanisms.b3.WorkspaceCoalitionProposal;
import dev.supplychain.agent.mechanisms.b3.WorkspaceSnapshot;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntention;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntentionId;
import dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment;
import dev.supplychain.agent.mechanisms.b6.UncertaintyDimension;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleState;
import dev.supplychain.agent.planning.api.PlanCandidate;
import dev.supplychain.agent.runtime.cognitive.decision.PlanningArtifactStore;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Captures the exact typed B1/B2/B3/B4/B6 implementation state from a disposable full-graph session. */
public final class FullGraphMechanismEvidenceCollector {
    public MechanismSpecificExperimentEvidence capture(FullGraphExperimentSession session,
            CognitiveCycleState state, MechanismTrainingArtifactBundle bundle) throws Exception {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(bundle, "bundle");
        String unit = session.unit().effectiveInputFingerprint();
        String fingerprint = bundle.artifactFingerprint();
        return switch (session.mechanism()) {
            case B1_MEMORY_LEARNING -> captureB1(session, unit, fingerprint, bundle);
            case B2_RECOVERY_PROCEDURE_REUSE -> captureB2(session, state, unit, fingerprint, bundle);
            case B3_COALITION_FEEDBACK -> captureB3(session, state, unit, fingerprint, bundle);
            case B4_INTENTION_METHOD_AUTHORITY -> captureB4(session, state, unit, fingerprint);
            case B6_UNCERTAINTY_CALIBRATION -> captureB6(session, state, unit, fingerprint);
        };
    }

    private static B1MemoryRunEvidence captureB1(FullGraphExperimentSession session, String unit,
            String bundleFingerprint, MechanismTrainingArtifactBundle bundle) {
        var scope = session.unit().fixture().planningRequest().workingMemory().scopeId();
        List<B1MemoryFeatureState> initialStates = session.initialB1MemoryFeatureStates().stream()
                .filter(value -> value.reference().scopeId().equals(scope)).toList();
        Map<String, String> before = fingerprints(initialStates);
        Map<String, String> expectedBefore = session.arm() == ExperimentArm.TREATMENT
                ? fingerprints(List.copyOf(bundle.b1().featureSeeds().values())) : Map.of();
        if (!before.equals(expectedBefore))
            throw new SecurityException("B1 isolated repository did not start from the exact arm-specific initial state");
        List<B1MemoryFeatureState> finalStates = session.runtime().b1MemoryFeatureRepository().snapshot().stream()
                .filter(value -> value.reference().scopeId().equals(scope)).toList();
        Map<String, String> after = fingerprints(finalStates);
        if (!after.keySet().containsAll(before.keySet()))
            throw new SecurityException("B1 seeded feature references are absent from the final per-session repository");
        Set<String> beforeOutcomeKeys = eventKeys(initialStates, true);
        Set<String> afterOutcomeKeys = eventKeys(finalStates, true);
        Set<String> beforeAccessKeys = eventKeys(initialStates, false);
        Set<String> afterAccessKeys = eventKeys(finalStates, false);
        afterOutcomeKeys.removeAll(beforeOutcomeKeys);
        afterAccessKeys.removeAll(beforeAccessKeys);
        return B1MemoryRunEvidence.create(session.arm(), unit, bundleFingerprint,
                bundle.b1().evidenceFingerprint(), before, after, afterOutcomeKeys.size(), afterAccessKeys.size());
    }

    static Map<String, String> fingerprints(List<B1MemoryFeatureState> states) {
        var result = new TreeMap<String, String>();
        for (B1MemoryFeatureState state : states) {
            String key = referenceKey(state);
            String canonical = key + "|" + state.accessHistory() + "|" + state.verifiedUseHistory() + "|"
                    + new TreeMap<>(state.cueAssociationStrengths()) + "|"
                    + Double.toHexString(state.expectedDownstreamUtility()) + "|" + state.usefulOutcomes() + "|"
                    + state.harmfulOutcomes() + "|" + state.neutralOutcomes() + "|"
                    + state.appliedOutcomeKeys().stream().sorted().toList() + "|"
                    + state.appliedAccessEventKeys().stream().sorted().toList() + "|" + state.revision() + "|" + state.updatedAt();
            if (result.putIfAbsent(key, ExperimentEvidenceFingerprint.sha256(canonical)) != null)
                throw new SecurityException("duplicate B1 feature reference in runtime snapshot");
        }
        return Map.copyOf(result);
    }

    private static String referenceKey(B1MemoryFeatureState state) {
        var reference = state.reference();
        return reference.scopeId().value() + "|" + reference.kind().name() + "|"
                + reference.candidateId() + "|" + reference.version();
    }

    private static Set<String> eventKeys(List<B1MemoryFeatureState> states, boolean outcomes) {
        var keys = new HashSet<String>();
        for (B1MemoryFeatureState state : states) {
            Set<String> source = outcomes ? state.appliedOutcomeKeys() : state.appliedAccessEventKeys();
            source.forEach(value -> keys.add(referenceKey(state) + "|" + value));
        }
        return keys;
    }

    private static B2ProcedureRunEvidence captureB2(FullGraphExperimentSession session, CognitiveCycleState state,
            String unit, String bundleFingerprint, MechanismTrainingArtifactBundle bundle) {
        PlanningArtifactStore.PlanningArtifacts planning = requiredPlanningArtifacts(session, state);
        var requestProcedures = planning.request().reusableRecoveryProcedures().stream()
                .sorted(java.util.Comparator.comparing(value -> value.artifact().conditionKey())).toList();
        List<ReusableRecoveryProcedure> expectedProcedures = session.arm() == ExperimentArm.TREATMENT
                ? bundle.b2().proceduresByCondition().values().stream()
                    .sorted(java.util.Comparator.comparing(value -> value.artifact().conditionKey())).toList()
                : List.of();
        if (!requestProcedures.equals(expectedProcedures)) {
            throw new SecurityException("B2 planning request does not contain the exact arm-specific reusable-procedure set");
        }

        List<B2ProcedureRunEvidence.ProcedureObservation> observations = new ArrayList<>();
        var suppliedActionIds = new java.util.TreeSet<String>();
        for (ReusableRecoveryProcedure source : requestProcedures) {
            var active = session.runtime().proceduralMemory().active(source.procedure().scopeId(),
                            source.procedure().procedureKey())
                    .orElseThrow(() -> new SecurityException("B2 treatment procedure was not rehydrated into the isolated active procedural repository"));
            if (active.status() != dev.supplychain.agent.memory.procedural.ProcedureStatus.ACTIVE
                    || !active.equals(source.procedure()) || !active.scopeId().equals(source.artifact().scopeId()))
                throw new SecurityException("B2 request procedure differs from the exact active repository version");
            var artifact = session.runtime().recoveryProcedureArtifactStore()
                    .retrieve(active.artifact(), active.scopeId())
                    .orElseThrow(() -> new SecurityException("B2 active procedure artifact is missing in its isolated store"));
            if (!artifact.equals(source.artifact()))
                throw new SecurityException("B2 treatment artifact bytes differ from the frozen approved TRAIN artifact");
            var actionIds = artifact.actions().stream().map(action -> action.actionId()).sorted().toList();
            suppliedActionIds.addAll(actionIds);
            observations.add(new B2ProcedureRunEvidence.ProcedureObservation(
                    artifact.conditionKey(), artifact.sourceDraftId(), artifact.sourceFingerprint(),
                    artifact.objectiveFingerprint(), active.status().name(), actionIds,
                    artifact.evidence().stream().map(ref -> ref.evidenceType() + ":" + ref.evidenceId()).sorted().toList()));
        }

        PlanCandidate selected = selectedCandidate(planning, state);
        List<String> proposedDescriptors = selected.actions().stream().map(action -> action.descriptorId().value())
                .distinct().sorted().toList();
        List<String> proposedReusableDescriptors = selected.actions().stream()
                .filter(planning.request()::isReusableRecoveryAction)
                .map(action -> action.descriptorId().value()).distinct().sorted().toList();
        return B2ProcedureRunEvidence.create(session.arm(), unit, bundleFingerprint, observations,
                List.copyOf(suppliedActionIds), proposedDescriptors, proposedReusableDescriptors);
    }

    private static PlanningArtifactStore.PlanningArtifacts requiredPlanningArtifacts(
            FullGraphExperimentSession session, CognitiveCycleState state) {
        String setId = state.text(CognitiveCycleState.CANDIDATE_SET_ID);
        if (setId.isBlank()) throw new SecurityException("B2 evidence requires the exact graph planning artifact reference");
        return session.runtime().planningArtifactStore().find(setId)
                .orElseThrow(() -> new SecurityException("B2 planning artifact is missing from the isolated planning artifact store"));
    }

    private static PlanCandidate selectedCandidate(PlanningArtifactStore.PlanningArtifacts artifacts,
            CognitiveCycleState state) {
        String selectedId = state.text(CognitiveCycleState.SELECTED_CANDIDATE_ID);
        if (selectedId.isBlank()) throw new SecurityException("B2 evidence requires an exact selected planner candidate");
        return artifacts.result().candidates().stream().filter(value -> value.candidateId().equals(selectedId))
                .findFirst().orElseThrow(() -> new SecurityException("selected candidate is absent from the complete planner artifact"));
    }

    private static B3CoalitionRunEvidence captureB3(FullGraphExperimentSession session, CognitiveCycleState state,
            String unit, String bundleFingerprint, MechanismTrainingArtifactBundle bundle) {
        String workspaceId = state.text(CognitiveCycleState.WORKSPACE_SNAPSHOT_ID);
        WorkspaceSnapshot snapshot = workspaceId.isBlank() ? null : session.runtime().b3WorkspaceRepository()
                .findById(workspaceId).orElseThrow(() -> new SecurityException("B3 graph workspace reference is missing from isolated repository"));
        List<B3CoalitionRunEvidence.ProposalObservation> proposals = new ArrayList<>();
        double totalApplied = 0.0;
        int coalitionCount = 0;
        String workspaceFingerprint = "";
        if (snapshot != null) {
            workspaceFingerprint = workspaceFingerprint(snapshot);
            coalitionCount = snapshot.coalitions().size();
            for (WorkspaceCoalitionProposal proposal : snapshot.evaluatedCoalitionProposals()) {
                double configured = session.runtime().coalitionOutcomeAdjustment()
                        .adjustmentFor(snapshot.scopeId(), proposal.preferenceKey());
                double applied = proposal.coalitionOutcomeAdjustment();
                if (proposal.selected()) totalApplied += applied;
                proposals.add(new B3CoalitionRunEvidence.ProposalObservation(proposal.preferenceKey(),
                        proposal.candidateIds(), proposal.selected(), configured, applied));
            }
        }
        boolean treatment = session.arm() == ExperimentArm.TREATMENT;
        String overlayFingerprint = treatment
                ? dev.supplychain.agent.mechanisms.b3.B3CoalitionFeedbackPolicyService.candidateFingerprint(bundle.b3().candidate()) : "";
        return B3CoalitionRunEvidence.create(session.arm(), unit, bundleFingerprint,
                workspaceId, workspaceFingerprint, overlayFingerprint, treatment,
                proposals, coalitionCount, totalApplied);
    }

    private static String workspaceFingerprint(WorkspaceSnapshot snapshot) {
        String canonical = snapshot.id() + "|" + snapshot.cycleId() + "|" + snapshot.scopeId().value() + "|"
                + snapshot.capacityUnits() + "|" + snapshot.usedUnits() + "|" + snapshot.status() + "|" + snapshot.version()
                + "|" + snapshot.entries().stream().map(entry -> entry.candidate().id() + ":"
                    + Double.toHexString(entry.marginalScore()) + ":" + entry.newlyCoveredTags().stream().sorted().toList()).toList()
                + "|" + snapshot.coalitions() + "|" + snapshot.evaluatedCoalitionProposals().stream()
                    .map(WorkspaceCoalitionProposal::fingerprint).toList();
        return ExperimentEvidenceFingerprint.sha256(canonical);
    }

    private static B4IntentionRunEvidence captureB4(FullGraphExperimentSession session, CognitiveCycleState state,
            String unit, String bundleFingerprint) {
        String id = state.text(CognitiveCycleState.INTENTION_ID);
        if (id.isBlank()) return B4IntentionRunEvidence.create(session.arm(), unit, bundleFingerprint,
                "", "", List.of(), "", "", "", "", 0, 0, List.of());
        RecoveryIntention finalIntention = session.runtime().intentionService().get(new RecoveryIntentionId(id));
        var entries = session.runtime().intentionTransitionRecorder().entries();
        RecoveryIntention initial = entries.isEmpty() ? finalIntention : entries.getFirst().before();
        List<B4IntentionRunEvidence.TransitionObservation> transitions = B4IntentionRunEvidence.observations(entries);
        return B4IntentionRunEvidence.create(session.arm(), unit, bundleFingerprint, id,
                finalIntention.objectiveFingerprint(), finalIntention.protectedConstraints().stream().map(Enum::name).sorted().toList(),
                initial.currentMethod().methodId(), initial.currentMethod().candidatePlanReference(),
                finalIntention.currentMethod().methodId(), finalIntention.currentMethod().candidatePlanReference(),
                initial.version(), finalIntention.version(), transitions);
    }

    private static B6CalibrationRunEvidence captureB6(FullGraphExperimentSession session, CognitiveCycleState state,
            String unit, String bundleFingerprint) {
        UncertaintyAssessment.Reference reference = state.uncertaintyAssessmentReference();
        if (reference == null) return B6CalibrationRunEvidence.create(session.arm(), unit, bundleFingerprint,
                "", 0, "", "", 0, "NOT_CALIBRATED", Map.of(), Map.of(), false);
        UncertaintyAssessment assessment = session.ports().lifecycleDomain()
                .currentUncertaintyAssessment(state.context(), session.ports().clock().instant());
        if (!reference.equals(assessment.reference()))
            throw new SecurityException("B6 assessment readback does not match the exact graph-carried assessment reference");
        var raw = values(assessment);
        String profileId = "";
        long profileVersion = 0;
        String targetId = "";
        Map<UncertaintyDimension, Double> observed = raw;
        boolean exactTrace = false;
        if (session.runtime().uncertaintyCalibration() != null) {
            var trace = session.runtime().uncertaintyCalibration().shadowTrace(reference);
            if (trace.isPresent()) {
                var exact = trace.orElseThrow();
                if (!exact.raw().reference().equals(reference)) throw new SecurityException("B6 shadow trace belongs to a different assessment");
                raw = values(exact.raw());
                observed = values(exact.transformed());
                profileId = exact.profileId();
                profileVersion = exact.profileVersion();
                targetId = exact.targetId();
                exactTrace = true;
            }
        }
        return B6CalibrationRunEvidence.create(session.arm(), unit, bundleFingerprint,
                profileId, profileVersion, targetId, reference.assessmentId(), reference.version(),
                assessment.calibration().status().name(), raw, observed, exactTrace);
    }

    private static Map<UncertaintyDimension, Double> values(UncertaintyAssessment assessment) {
        var values = new EnumMap<UncertaintyDimension, Double>(UncertaintyDimension.class);
        assessment.signals().forEach((dimension, signal) -> values.put(dimension, signal.uncertainty()));
        return Map.copyOf(values);
    }
}
