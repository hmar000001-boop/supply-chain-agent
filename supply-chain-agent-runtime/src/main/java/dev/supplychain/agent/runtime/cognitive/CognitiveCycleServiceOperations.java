package dev.supplychain.agent.runtime.cognitive;

import dev.supplychain.agent.mechanisms.b6.MetaControlDecision;
import dev.supplychain.agent.runtime.cognitive.decision.CognitiveCycleDecisionServices;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecycleServices;

import java.util.Objects;

/**
 * Complete M6 composition of the real M2/B1/B3/M5 decision services and M2/M3/B2/B4/B6 lifecycle
 * services. There is no catch-all host delegate; external-system and identity adapters remain
 * explicit dependencies of the lifecycle services.
 */
public final class CognitiveCycleServiceOperations implements CognitiveCycleOperations {
    private final CognitiveCycleDecisionServices decisions;
    private final CognitiveCycleLifecycleServices lifecycle;

    public CognitiveCycleServiceOperations(CognitiveCycleDecisionServices decisions,
                                           CognitiveCycleLifecycleServices lifecycle) {
        this.decisions = Objects.requireNonNull(decisions, "decisions");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        if (lifecycle.decisionServices() != decisions) {
            throw new IllegalArgumentException("decision and lifecycle composition must share the same decision-service instance");
        }
    }

    @Override public InterpretationResult interpret(CycleContext context) { return lifecycle.interpret(context); }
    @Override public void recordClarification(CycleContext context) { lifecycle.recordClarification(context); }
    @Override public ContextLoaded loadContext(CycleContext context) { return lifecycle.loadContext(context); }
    @Override public EvidenceRetrieved retrieveEvidence(CycleContext context) { return decisions.retrieveEvidence(context); }
    @Override public EvidenceAccepted acceptExternalEvidence(CycleContext context) { return lifecycle.acceptExternalEvidence(context); }
    @Override public WorkspacePrepared prepareWorkspace(CycleContext context) { return decisions.prepareWorkspace(context); }
    @Override public PlanningPrepared proposeCandidates(CycleContext context) { return decisions.proposeCandidates(context); }
    @Override public CandidateEvaluation evaluateCandidates(CycleContext context) { return decisions.evaluateCandidates(context); }
    @Override public ImpasseOpened openImpasse(CycleContext context) { return lifecycle.openImpasse(context); }
    @Override public ImpasseInvestigated investigateImpasse(CycleContext context) { return lifecycle.investigateImpasse(context); }
    @Override public ImpasseResolved validateImpasseReturn(CycleContext context) { return lifecycle.validateImpasseReturn(context); }
    @Override public IntentionGovernance governIntention(CycleContext context) { return lifecycle.governIntention(context); }
    @Override public MetaControlDecision governMetaControl(CycleContext context) { return lifecycle.governMetaControl(context); }
    @Override public HumanControlResolution resolveHumanControl(CycleContext context) { return lifecycle.resolveHumanControl(context); }
    @Override public AuthorizationPrepared prepareAuthorization(CycleContext context) { return lifecycle.prepareAuthorization(context); }
    @Override public ApprovalResolved resolveApproval(CycleContext context) { return lifecycle.resolveApproval(context); }
    @Override public PlanRevalidation revalidatePlan(CycleContext context) { return lifecycle.revalidatePlan(context); }
    @Override public AuthorizedExecution executeAuthorizedCandidate(CycleContext context) { return lifecycle.executeAuthorizedCandidate(context); }
    @Override public ExternalObservation observeAndReconcile(CycleContext context) { return lifecycle.observeAndReconcile(context); }
    @Override public WorkingMemoryUpdated updateWorkingMemoryFromObservation(CycleContext context, ExternalObservation observation) {
        return lifecycle.updateWorkingMemoryFromObservation(context, observation);
    }
    @Override public WorkingMemoryUpdated updateWorkingMemoryFromStoredObservation(CycleContext context) {
        return lifecycle.updateWorkingMemoryFromStoredObservation(context);
    }
    @Override public EpisodeRecorded recordOutcomeEpisode(CycleContext context) { return lifecycle.recordOutcomeEpisode(context); }
    @Override public void recordEscalation(CycleContext context, String reason) { lifecycle.recordEscalation(context, reason); }
}
