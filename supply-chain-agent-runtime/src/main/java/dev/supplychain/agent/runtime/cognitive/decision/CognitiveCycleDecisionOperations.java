package dev.supplychain.agent.runtime.cognitive.decision;

import dev.supplychain.agent.mechanisms.b6.MetaControlDecision;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;

import java.util.Objects;

/**
 * Graph-facing decorator that routes the M2/B1/B3/M5 decision stages through concrete services.
 *
 * <p>The remaining control, authority and grounding stages are delegated to the host composition
 * until M6-B supplies their service-backed implementation. This keeps the first delivery useful
 * without installing placeholder implementations for approvals or external effects.</p>
 */
public final class CognitiveCycleDecisionOperations implements CognitiveCycleOperations {
    private final CognitiveCycleOperations remainingOperations;
    private final CognitiveCycleDecisionServices decisionServices;

    public CognitiveCycleDecisionOperations(CognitiveCycleOperations remainingOperations,
                                            CognitiveCycleDecisionServices decisionServices) {
        this.remainingOperations = Objects.requireNonNull(remainingOperations, "remainingOperations");
        this.decisionServices = Objects.requireNonNull(decisionServices, "decisionServices");
    }

    @Override public EvidenceRetrieved retrieveEvidence(CycleContext context) {
        return decisionServices.retrieveEvidence(context);
    }

    @Override public WorkspacePrepared prepareWorkspace(CycleContext context) {
        return decisionServices.prepareWorkspace(context);
    }

    @Override public PlanningPrepared proposeCandidates(CycleContext context) {
        return decisionServices.proposeCandidates(context);
    }

    @Override public CandidateEvaluation evaluateCandidates(CycleContext context) {
        return decisionServices.evaluateCandidates(context);
    }

    @Override public InterpretationResult interpret(CycleContext context) {
        return remainingOperations.interpret(context);
    }

    @Override public void recordClarification(CycleContext context) {
        remainingOperations.recordClarification(context);
    }

    @Override public ContextLoaded loadContext(CycleContext context) {
        return remainingOperations.loadContext(context);
    }

    @Override public EvidenceAccepted acceptExternalEvidence(CycleContext context) {
        return remainingOperations.acceptExternalEvidence(context);
    }

    @Override public ImpasseOpened openImpasse(CycleContext context) {
        return remainingOperations.openImpasse(context);
    }

    @Override public ImpasseInvestigated investigateImpasse(CycleContext context) {
        return remainingOperations.investigateImpasse(context);
    }

    @Override public ImpasseResolved validateImpasseReturn(CycleContext context) {
        return remainingOperations.validateImpasseReturn(context);
    }

    @Override public IntentionGovernance governIntention(CycleContext context) {
        return remainingOperations.governIntention(context);
    }

    @Override public MetaControlDecision governMetaControl(CycleContext context) {
        return remainingOperations.governMetaControl(context);
    }

    @Override public HumanControlResolution resolveHumanControl(CycleContext context) {
        return remainingOperations.resolveHumanControl(context);
    }

    @Override public AuthorizationPrepared prepareAuthorization(CycleContext context) {
        return remainingOperations.prepareAuthorization(context);
    }

    @Override public ApprovalResolved resolveApproval(CycleContext context) {
        return remainingOperations.resolveApproval(context);
    }

    @Override public PlanRevalidation revalidatePlan(CycleContext context) {
        return remainingOperations.revalidatePlan(context);
    }

    @Override public AuthorizedExecution executeAuthorizedCandidate(CycleContext context) {
        return remainingOperations.executeAuthorizedCandidate(context);
    }

    @Override public ExternalObservation observeAndReconcile(CycleContext context) {
        return remainingOperations.observeAndReconcile(context);
    }

    @Override public WorkingMemoryUpdated updateWorkingMemoryFromObservation(
            CycleContext context, ExternalObservation observation) {
        return remainingOperations.updateWorkingMemoryFromObservation(context, observation);
    }

    @Override public WorkingMemoryUpdated updateWorkingMemoryFromStoredObservation(CycleContext context) {
        return remainingOperations.updateWorkingMemoryFromStoredObservation(context);
    }

    @Override public EpisodeRecorded recordOutcomeEpisode(CycleContext context) {
        return remainingOperations.recordOutcomeEpisode(context);
    }

    @Override public void recordEscalation(CycleContext context, String reason) {
        remainingOperations.recordEscalation(context, reason);
    }
}
