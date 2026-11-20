package dev.supplychain.agent.runtime.cognitive.lifecycle;

import dev.supplychain.agent.actions.api.ActionContext;
import dev.supplychain.agent.actions.api.ActionDescriptor;
import dev.supplychain.agent.actions.api.ActionDispatcher;
import dev.supplychain.agent.actions.api.ActionEffect;
import dev.supplychain.agent.actions.api.ActionRegistry;
import dev.supplychain.agent.actions.api.ActionStatus;
import dev.supplychain.agent.actions.internal.ReasoningAction;
import dev.supplychain.agent.core.interpretation.RequestInterpreter;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.episodic.EpisodicMemoryService;
import dev.supplychain.agent.memory.working.MemoryValue;
import dev.supplychain.agent.memory.working.WorkingMemoryId;
import dev.supplychain.agent.memory.working.WorkingMemoryService;
import dev.supplychain.agent.memory.working.WorkingMemorySnapshot;
import dev.supplychain.agent.mechanisms.b2.ChildResolutionResult;
import dev.supplychain.agent.mechanisms.b2.ImpasseCaseStatus;
import dev.supplychain.agent.mechanisms.b2.ImpasseResolutionService;
import dev.supplychain.agent.mechanisms.b2.ParentContextSnapshot;
import dev.supplychain.agent.mechanisms.b2.ResolutionDisposition;
import dev.supplychain.agent.mechanisms.b2.ResolutionFinding;
import dev.supplychain.agent.mechanisms.b4.DelegationReturn;
import dev.supplychain.agent.mechanisms.b4.IntentionStatus;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntention;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntentionId;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntentionService;
import dev.supplychain.agent.mechanisms.b6.MetaControlGovernor;
import dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;
import dev.supplychain.agent.runtime.cognitive.ResourceBudgetExecutionGuard;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations.*;
import dev.supplychain.agent.runtime.cognitive.decision.CognitiveCycleDecisionServices;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleAuthorityPort.ApprovalBinding;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort.EpisodeDraft;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort.ExternalEvidenceDecision;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort.IntentionDirective;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Service-backed implementation of the remaining CoALA/Fan-Lan lifecycle operations.
 * Domain facts, host identity, evidence ingestion and ERP/carrier reconciliation remain explicit
 * ports; all memory, B2/B4/B6 and M3 dispatch behavior is performed by the actual services.
 */
public final class CognitiveCycleLifecycleServices {
    private static final String OBSERVATION_KEY_PREFIX = "observation:";

    private final WorkingMemoryService workingMemory;
    private final EpisodicMemoryService episodicMemory;
    private final ImpasseResolutionService impasses;
    private final RecoveryIntentionService intentions;
    private final MetaControlGovernor metaControl;
    private final ActionDispatcher actionDispatcher;
    private final ActionRegistry actionRegistry;
    private final RequestInterpreter interpreter;
    private final CognitiveCycleDecisionServices decisionServices;
    private final CognitiveCycleLifecyclePort domain;
    private final CognitiveCycleAuthorityPort authority;
    private final AuthorizedActionPlanStore actionPlans;
    private final ImpasseChildArtifactStore childArtifacts;
    private final ActionExecutionBatchStore actionBatches;
    private final ObservationStore observations;
    private final ChildInvestigationStepStore childSteps;
    private final Clock clock;
    private final ResourceBudgetExecutionGuard resourceBudgetGuard;

    public CognitiveCycleLifecycleServices(
            WorkingMemoryService workingMemory,
            EpisodicMemoryService episodicMemory,
            ImpasseResolutionService impasses,
            RecoveryIntentionService intentions,
            MetaControlGovernor metaControl,
            ActionDispatcher actionDispatcher,
            ActionRegistry actionRegistry,
            RequestInterpreter interpreter,
            CognitiveCycleDecisionServices decisionServices,
            CognitiveCycleLifecyclePort domain,
            CognitiveCycleAuthorityPort authority,
            AuthorizedActionPlanStore actionPlans,
            ImpasseChildArtifactStore childArtifacts,
            ActionExecutionBatchStore actionBatches,
            ObservationStore observations,
            Clock clock) {
        this(workingMemory, episodicMemory, impasses, intentions, metaControl, actionDispatcher, actionRegistry,
                interpreter, decisionServices, domain, authority, actionPlans, childArtifacts, actionBatches,
                observations, null, clock);
    }

    /** Production constructor: inject a durable child-step receipt store for crash-safe B2 resume. */
    public CognitiveCycleLifecycleServices(
            WorkingMemoryService workingMemory,
            EpisodicMemoryService episodicMemory,
            ImpasseResolutionService impasses,
            RecoveryIntentionService intentions,
            MetaControlGovernor metaControl,
            ActionDispatcher actionDispatcher,
            ActionRegistry actionRegistry,
            RequestInterpreter interpreter,
            CognitiveCycleDecisionServices decisionServices,
            CognitiveCycleLifecyclePort domain,
            CognitiveCycleAuthorityPort authority,
            AuthorizedActionPlanStore actionPlans,
            ImpasseChildArtifactStore childArtifacts,
            ActionExecutionBatchStore actionBatches,
            ObservationStore observations,
            ChildInvestigationStepStore childSteps,
            Clock clock) {
        this(workingMemory, episodicMemory, impasses, intentions, metaControl, actionDispatcher, actionRegistry,
                interpreter, decisionServices, domain, authority, actionPlans, childArtifacts, actionBatches,
                observations, childSteps, null, clock);
    }

    /** Production constructor with the same durable B6 execution guard used by decision services. */
    public CognitiveCycleLifecycleServices(
            WorkingMemoryService workingMemory,
            EpisodicMemoryService episodicMemory,
            ImpasseResolutionService impasses,
            RecoveryIntentionService intentions,
            MetaControlGovernor metaControl,
            ActionDispatcher actionDispatcher,
            ActionRegistry actionRegistry,
            RequestInterpreter interpreter,
            CognitiveCycleDecisionServices decisionServices,
            CognitiveCycleLifecyclePort domain,
            CognitiveCycleAuthorityPort authority,
            AuthorizedActionPlanStore actionPlans,
            ImpasseChildArtifactStore childArtifacts,
            ActionExecutionBatchStore actionBatches,
            ObservationStore observations,
            ChildInvestigationStepStore childSteps,
            ResourceBudgetExecutionGuard resourceBudgetGuard,
            Clock clock) {
        this.workingMemory = Objects.requireNonNull(workingMemory, "workingMemory");
        this.episodicMemory = Objects.requireNonNull(episodicMemory, "episodicMemory");
        this.impasses = Objects.requireNonNull(impasses, "impasses");
        this.intentions = Objects.requireNonNull(intentions, "intentions");
        this.metaControl = Objects.requireNonNull(metaControl, "metaControl");
        this.actionDispatcher = Objects.requireNonNull(actionDispatcher, "actionDispatcher");
        this.actionRegistry = Objects.requireNonNull(actionRegistry, "actionRegistry");
        this.interpreter = Objects.requireNonNull(interpreter, "interpreter");
        this.decisionServices = Objects.requireNonNull(decisionServices, "decisionServices");
        this.domain = Objects.requireNonNull(domain, "domain");
        this.authority = Objects.requireNonNull(authority, "authority");
        this.actionPlans = Objects.requireNonNull(actionPlans, "actionPlans");
        this.childArtifacts = Objects.requireNonNull(childArtifacts, "childArtifacts");
        this.actionBatches = Objects.requireNonNull(actionBatches, "actionBatches");
        this.observations = Objects.requireNonNull(observations, "observations");
        this.childSteps = childSteps;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.resourceBudgetGuard = resourceBudgetGuard;
        validateBudgetedActionBindings();
    }

    public CognitiveCycleDecisionServices decisionServices() { return decisionServices; }
    public ResourceBudgetExecutionGuard resourceBudgetGuard() { return resourceBudgetGuard; }

    public InterpretationResult interpret(CycleContext context) {
        var interpreted = Objects.requireNonNull(interpreter.interpret(
                domain.interpretationRequest(context, clock.instant())), "request interpreter returned null");
        String normalized = interpreted.normalizedIntent().isBlank()
                ? required(context.request(), "request") : interpreted.normalizedIntent();
        String summary = interpreted.summary().isBlank()
                ? "Interpretation outcome: " + interpreted.outcome() : interpreted.summary();
        return new InterpretationResult(interpreted.outcome(), normalized, summary);
    }

    public void recordClarification(CycleContext context) {
        if (context.clarificationContext().isBlank()) {
            throw new IllegalStateException("clarification was not supplied by the host; no synthetic clarification is allowed");
        }
        domain.persistClarification(context, clock.instant());
    }

    public ContextLoaded loadContext(CycleContext context) {
        MemoryScopeId scope = requireScope(context);
        Instant now = clock.instant();
        WorkingMemorySnapshot memory;
        if (context.workingMemoryId().isBlank()) {
            WorkingMemoryId stableMemoryId = Objects.requireNonNull(domain.workingMemoryId(context), "domain returned null stable working-memory identity");
            memory = workingMemory.open(stableMemoryId, scope, now);
        } else {
            memory = workingMemory.load(new WorkingMemoryId(context.workingMemoryId()));
            if (!memory.scopeId().equals(scope)) {
                throw new SecurityException("working memory belongs to a different scope");
            }
        }
        memory = applyContextFacts(context, memory, now);

        RecoveryIntention intention;
        if (context.intentionId().isBlank()) {
            var seed = domain.intentionSeed(context, now).orElseThrow(() ->
                    new IllegalStateException("a new cognitive cycle requires an authoritative active recovery objective, stable intention identity and initial method"));
            intention = intentions.find(seed.intentionId()).map(existing -> {
                if (!existing.scopeId().equals(scope)
                        || !existing.objectiveId().equals(seed.objective().id())
                        || !existing.objectiveFingerprint().equals(dev.supplychain.agent.mechanisms.b4.ObjectiveFingerprint.from(seed.objective()).value())
                        || existing.status() == IntentionStatus.COMPLETED
                        || existing.status() == IntentionStatus.TERMINATED) {
                    throw new SecurityException("stable intention identity is already bound to a different or terminal objective");
                }
                return existing;
            }).orElseGet(() -> intentions.create(seed.intentionId(), seed.objective(), scope,
                    seed.owner(), seed.initialMethod(), now));
        } else {
            intention = intentions.get(new RecoveryIntentionId(context.intentionId()));
            if (!intention.scopeId().equals(scope)) throw new SecurityException("recovery intention belongs to a different memory scope");
        }
        return new ContextLoaded(memory.id().value(), memory.version(), intention.id().value(), intention.version());
    }

    public EvidenceAccepted acceptExternalEvidence(CycleContext context) {
        Instant now = clock.instant();
        ExternalEvidenceDecision decision = Objects.requireNonNull(domain.acceptExternalEvidence(context, now), "external evidence decision");
        if (decision.acceptance() == EvidenceAcceptance.ACCEPTED) {
            WorkingMemorySnapshot memory = currentMemory(context);
            memory = applyFacts(context, memory, decision.facts(), now);
        }
        return new EvidenceAccepted(decision.acceptance(), decision.evidenceSetId(), decision.reason());
    }

    public EvidenceRetrieved retrieveEvidence(CycleContext context) { return decisionServices.retrieveEvidence(context); }
    public WorkspacePrepared prepareWorkspace(CycleContext context) { return decisionServices.prepareWorkspace(context); }
    public PlanningPrepared proposeCandidates(CycleContext context) { return decisionServices.proposeCandidates(context); }
    public CandidateEvaluation evaluateCandidates(CycleContext context) { return decisionServices.evaluateCandidates(context); }

    public ImpasseOpened openImpasse(CycleContext context) {
        Instant now = clock.instant();
        requireCurrentUncertainty(context, now);
        WorkingMemorySnapshot memory = currentMemory(context);
        RecoveryIntention intention = currentIntention(context);
        var request = Objects.requireNonNull(domain.impasseOpenRequest(context, now), "impasse open request");
        ParentContextSnapshot parent = new ParentContextSnapshot(memory.scopeId(), context.requestId(),
                memory.version(), required(domain.objectiveFingerprint(context), "objective fingerprint"));
        var opened = impasses.open(request.caseId(), memory.scopeId(), context.executionId(), parent,
                request.signal(), request.parentCapabilities(), request.childCapabilities(), now,
                request.deadline(), request.maximumChildActions());
        boolean delegated = false;
        try {
            if (intention.status() != IntentionStatus.ACTIVE || intention.version() != context.intentionVersion()) {
                throw new IllegalStateException("only the current active intention may delegate an impasse investigation");
            }
            intentions.delegate(intention.id(), intention.version(), opened.id(), now);
            delegated = true;
            // Starting the B2 child is part of the same logical transition as B4 delegation.
            // If it fails, compensate both records rather than leaving a parent intention stranded.
            impasses.start(opened.id(), opened.version(), now);
        } catch (RuntimeException failure) {
            try {
                var latestCase = impasses.get(opened.id());
                if (latestCase.status() == ImpasseCaseStatus.OPEN || latestCase.status() == ImpasseCaseStatus.INVESTIGATING) {
                    impasses.reject(opened.id(), latestCase.version(), "B2/B4 start failed: " + safeMessage(failure), clock.instant());
                }
            } catch (RuntimeException compensationFailure) {
                failure.addSuppressed(compensationFailure);
            }
            if (delegated) {
                try {
                    var latestIntention = intentions.get(intention.id());
                    if (latestIntention.status() == IntentionStatus.DELEGATED
                            && opened.id().equals(latestIntention.delegationId())) {
                        intentions.returnFromDelegation(latestIntention.id(), latestIntention.version(), opened.id(),
                                DelegationReturn.NO_METHOD_CHANGE, clock.instant());
                    }
                } catch (RuntimeException compensationFailure) {
                    failure.addSuppressed(compensationFailure);
                }
            }
            throw failure;
        }
        return new ImpasseOpened(opened.id(), request.signal().summary());
    }

    public ImpasseInvestigated investigateImpasse(CycleContext context) {
        String caseId = required(context.impasseCaseId(), "impasseCaseId");
        Instant now = clock.instant();
        requireCurrentUncertainty(context, now);
        var resolutionCase = impasses.get(caseId);
        if (!resolutionCase.executionId().equals(context.executionId())
                || !resolutionCase.parentRequestId().equals(context.requestId())) {
            throw new SecurityException("B2 case does not belong to the active request/execution");
        }
        var plan = Objects.requireNonNull(domain.planChildInvestigation(context, resolutionCase, now), "B2 child investigation plan");
        List<ResolutionFinding> findings = new ArrayList<>();
        var seenStepIds = new java.util.HashSet<String>();
        long version = resolutionCase.version();
        for (var step : plan.steps()) {
            if (!seenStepIds.add(step.stepId())) throw new SecurityException("B2 child plan contains a duplicate step identity");
            if (!resolutionCase.childCapabilities().contains(step.capability())) {
                throw new SecurityException("B2 child investigation attempted an undelegated capability: " + step.capability());
            }
            long fencingToken = 0L;
            if (childSteps != null) {
                String inputFingerprint = sha256(resolutionCase.id() + "|" + resolutionCase.executionId() + "|"
                        + resolutionCase.parentWorkingMemoryVersion() + "|" + resolutionCase.objectiveFingerprint() + "|"
                        + step.stepId() + "|" + step.capability() + "|" + step.description());
                ChildInvestigationStepStore.StepClaim claim = childSteps.claim(caseId, step.stepId(), inputFingerprint, clock.instant());
                if (claim.status() == ChildInvestigationStepStore.Status.COMPLETED) {
                    findings.addAll(claim.findings());
                    continue;
                }
                if (claim.status() == ChildInvestigationStepStore.Status.IN_PROGRESS) {
                    throw new IllegalStateException("B2 child step is already being investigated by another worker: " + step.stepId());
                }
                if (claim.status() == ChildInvestigationStepStore.Status.UNKNOWN_OUTCOME) {
                    throw new IllegalStateException("B2 child step outcome requires explicit reconciliation: " + step.stepId());
                }
                fencingToken = claim.fencingToken();
            }
            try {
                // The domain lookup is read-only, but recordChildAction commits local case/budget state first.
                // An interrupted step is therefore reconciled instead of blindly repeating that transition.
                var updated = impasses.recordChildAction(caseId, version, step.capability(), clock.instant());
                version = updated.version();
                List<ResolutionFinding> stepFindings = Objects.requireNonNull(
                        domain.executeChildInvestigationStep(context, updated, step, clock.instant()),
                        "B2 child step returned null findings");
                if (childSteps != null) childSteps.complete(caseId, step.stepId(), fencingToken, stepFindings, clock.instant());
                findings.addAll(stepFindings);
            } catch (RuntimeException failure) {
                if (childSteps != null) {
                    try { childSteps.fail(caseId, step.stepId(), fencingToken, safeMessage(failure), true, clock.instant()); }
                    catch (RuntimeException receiptFailure) { failure.addSuppressed(receiptFailure); }
                }
                throw failure;
            }
        }
        ResolutionDisposition disposition = plan.disposition();
        if (findings.isEmpty()) disposition = ResolutionDisposition.UNRESOLVED;
        else if (disposition == ResolutionDisposition.UNRESOLVED) {
            throw new IllegalStateException("B2 domain adapter returned findings for an unresolved disposition");
        }
        ChildResolutionResult result = new ChildResolutionResult(caseId, resolutionCase.parentRequestId(),
                resolutionCase.parentWorkingMemoryVersion(), resolutionCase.objectiveFingerprint(),
                disposition, findings, plan.assumptions(), clock.instant());
        childArtifacts.save(caseId, result, plan.intentionReturn());
        return new ImpasseInvestigated("b2-child-result:" + caseId,
                "steps=" + plan.steps().size() + ";findings=" + findings.size() + ";disposition=" + disposition);
    }

    public ImpasseResolved validateImpasseReturn(CycleContext context) {
        String caseId = required(context.impasseCaseId(), "impasseCaseId");
        Instant now = clock.instant();
        requireCurrentUncertainty(context, now);
        var artifact = childArtifacts.find(caseId).orElseThrow(() ->
                new IllegalStateException("B2 child result is missing; parent context cannot accept an unverified return"));
        var resolutionCase = impasses.get(caseId);
        WorkingMemorySnapshot latestMemory = currentMemory(context);
        ParentContextSnapshot currentParent = domain.currentParentContext(context, latestMemory);
        if (!currentParent.scopeId().equals(latestMemory.scopeId())
                || !currentParent.requestId().equals(context.requestId())
                || currentParent.workingMemoryVersion() != latestMemory.version()
                || !currentParent.objectiveFingerprint().equals(domain.objectiveFingerprint(context))) {
            throw new SecurityException("domain adapter parent snapshot differs from authoritative working memory or objective");
        }
        var resolved = impasses.resolve(caseId, resolutionCase.version(), artifact.result(), currentParent, now);
        RecoveryIntention intention = intentions.get(new RecoveryIntentionId(context.intentionId()));
        if (intention.status() == IntentionStatus.DELEGATED) {
            if (!caseId.equals(intention.delegationId())) throw new SecurityException("B2 return does not match the intention's delegated child");
            intentions.returnFromDelegation(intention.id(), intention.version(), caseId,
                    artifact.intentionReturn(), now);
        }
        if (resolved.status() == ImpasseCaseStatus.RESOLVED) {
            return new ImpasseResolved(ImpasseDisposition.RESOLVED, "b2-child-result:" + caseId,
                    "parent version, objective fingerprint and evidence-backed child findings validated");
        }
        return new ImpasseResolved(ImpasseDisposition.UNRESOLVED, "",
                resolved.terminalReason() == null ? "B2 child did not resolve the impasse" : resolved.terminalReason());
    }

    public IntentionGovernance governIntention(CycleContext context) {
        Instant now = clock.instant();
        requireCurrentUncertainty(context, now);
        RecoveryIntention intention = currentIntention(context);
        if (intention.version() != context.intentionVersion()) {
            throw new IllegalStateException("B4 intention version changed; reload the active context before governance");
        }
        IntentionDirective directive = Objects.requireNonNull(domain.intentionDirective(context, intention, now), "B4 intention directive");
        RecoveryIntention updated = intention;
        switch (directive.action()) {
            case CONTINUE -> {
                if (intention.status() != IntentionStatus.ACTIVE) throw new IllegalStateException("B4 cannot continue a non-active intention");
            }
            case RECONSIDER -> updated = intentions.requestReconsideration(intention.id(), intention.version(), directive.reason(), now);
            case CHANGE_METHOD -> updated = intentions.changeMethod(intention.id(), intention.version(),
                    directive.replacementMethod(), directive.assertedObjectiveFingerprint(), now);
            case SUSPEND -> updated = intentions.suspend(intention.id(), intention.version(), directive.reason(), now);
            case TERMINATE -> {
                if (!authority.isTerminationAuthorized(context, directive.actorId(), now)) {
                    throw new SecurityException("B4 termination requires an authenticated and authorized operator");
                }
                updated = intentions.terminate(intention.id(), intention.version(), directive.actorId(), directive.reason(), now);
            }
            case ESCALATE -> { /* Escalation is recorded by the graph's terminal path; objective remains protected. */ }
        }
        return new IntentionGovernance(directive.action(), updated.id().value(), updated.version(), directive.reason());
    }

    public UncertaintyAssessment.Reference assessUncertainty(CycleContext context, String triggerId) {
        Objects.requireNonNull(context, "context");
        String stableTriggerId = required(triggerId, "triggerId");
        Instant now = clock.instant();
        var previous = context.uncertaintyAssessmentReference();
        String requestIdempotencyKey = uncertaintyAssessmentRequestKey(
                stableTriggerId, context.executionId(), context.memoryScopeId(), previous);
        UncertaintyAssessment assessment = Objects.requireNonNull(
                domain.assessUncertainty(context, requestIdempotencyKey, now),
                "domain returned null uncertainty assessment");
        requireCurrentAssessment(assessment, context.executionId(), context.memoryScopeId(), now);
        if (previous == null) {
            if (assessment.reference().version() != 1L) {
                throw new IllegalStateException("first uncertainty assessment revision must be version 1");
            }
        } else if (!assessment.reference().assessmentId().equals(previous.assessmentId())
                || assessment.reference().version() != previous.version() + 1L) {
            throw new SecurityException("uncertainty assessment revision must advance exactly one version in the same execution-scoped assessment");
        }
        return assessment.reference();
    }

    public dev.supplychain.agent.mechanisms.b6.MetaControlDecision governMetaControl(CycleContext context) {
        Instant now = clock.instant();
        var expectedReference = context.requireUncertaintyAssessmentReference();
        CognitiveCycleLifecyclePort.MetaControlInputs inputs = Objects.requireNonNull(domain.metaControlInputs(context, now), "B6 meta-control inputs");
        if (!inputs.budget().executionId().equals(context.executionId())) {
            throw new SecurityException("B6 budget snapshot belongs to a different execution");
        }
        if (!inputs.uncertainty().reference().equals(expectedReference)) {
            throw new SecurityException("B6 uncertainty assessment differs from the exact revision carried by graph state");
        }
        requireCurrentAssessment(inputs.uncertainty(), context.executionId(), context.memoryScopeId(), now);
        if (resourceBudgetGuard != null) {
            var durable = resourceBudgetGuard.snapshot(context.executionId(), now);
            if (!durable.budget().equals(inputs.budget().budget())) {
                throw new SecurityException("domain B6 budget definition differs from the durable execution account");
            }
            inputs = new CognitiveCycleLifecyclePort.MetaControlInputs(durable, inputs.uncertainty(), inputs.requiredForNextStep());
        }
        var decision = metaControl.evaluate(inputs.budget(), inputs.uncertainty(),
                inputs.requiredForNextStep(), now);
        domain.recordMetaControlDecision(context, inputs, decision, clock.instant());
        return decision;
    }

    private UncertaintyAssessment requireCurrentUncertainty(CycleContext context, Instant at) {
        var reference = context.requireUncertaintyAssessmentReference();
        UncertaintyAssessment assessment = Objects.requireNonNull(
                domain.currentUncertaintyAssessment(context, at), "domain returned null current uncertainty assessment");
        if (!assessment.reference().equals(reference)) {
            throw new SecurityException("host uncertainty store did not resolve the exact graph-referenced revision");
        }
        requireCurrentAssessment(assessment, context.executionId(), context.memoryScopeId(), at);
        return assessment;
    }

    private static void requireCurrentAssessment(UncertaintyAssessment assessment, String executionId,
                                                 String memoryScopeId, Instant at) {
        if (!assessment.isCurrentFor(executionId, memoryScopeId, at)) {
            throw new SecurityException("uncertainty assessment is expired or belongs to another execution or memory scope");
        }
        if (assessment.provenanceReference().isBlank()) {
            throw new SecurityException("uncertainty assessment is missing provenance");
        }
    }

    public HumanControlResolution resolveHumanControl(CycleContext context) {
        Instant now = clock.instant();
        HumanControlResolution resolution = authority.resolveHumanControl(context, now);
        if (!resolution.responseReference().equals(context.humanControlResponseId())
                || !resolution.actorId().equals(context.humanControlActorId())) {
            throw new SecurityException("trusted authority response does not match the graph's host-issued identity references");
        }
        if (!context.intentionId().isBlank()) {
            RecoveryIntention intention = intentions.get(new RecoveryIntentionId(context.intentionId()));
            if (intention.status() == IntentionStatus.SUSPENDED) {
                if (resolution.action() == HumanControlAction.ABORT) {
                    intentions.terminate(intention.id(), intention.version(), resolution.actorId(), resolution.reason(), now);
                } else {
                    intentions.resume(intention.id(), intention.version(), now);
                }
            }
        }
        return resolution;
    }

    public AuthorizationPrepared prepareAuthorization(CycleContext context) {
        try {
            requireCurrentUncertainty(context, clock.instant());
        } catch (IllegalStateException | SecurityException staleAssessment) {
            return new AuthorizationPrepared(AuthorizationDisposition.DENIED, true, "",
                    "authorization refused because the scoped uncertainty assessment is missing, stale or expired");
        }
        AuthorizedActionPlan plan = actionPlan(context);
        if (!plan.stateChanging()) throw new SecurityException("recovery plan was not classified as a state-changing action plan");
        var binding = binding(context, plan);
        AuthorizationPrepared decision = Objects.requireNonNull(
                authority.requestApproval(context, binding, plan.descriptors(), clock.instant()),
                "authority returned null authorization result");
        if (decision.stateChanging() != plan.stateChanging()) {
            throw new SecurityException("authority classification differs from the trusted M3 action descriptors");
        }
        if (decision.disposition() == AuthorizationDisposition.NO_APPROVAL_REQUIRED) {
            throw new SecurityException("state-changing supply-chain recovery action cannot bypass explicit approval");
        }
        if (decision.disposition() == AuthorizationDisposition.REQUIRES_APPROVAL && decision.approvalId().isBlank()) {
            throw new IllegalStateException("approval-required authority response has no durable approval reference");
        }
        return decision;
    }

    public ApprovalResolved resolveApproval(CycleContext context) {
        requireCurrentUncertainty(context, clock.instant());
        AuthorizedActionPlan plan = actionPlan(context);
        if (context.approvalId().isBlank() || context.approvalResponseId().isBlank() || context.approverId().isBlank()) {
            throw new IllegalStateException("approval cannot be resolved without host-issued approval, response and approver references");
        }
        ApprovalResolved resolved = Objects.requireNonNull(
                authority.resolveApproval(context, binding(context, plan), clock.instant()),
                "authority returned null approval resolution");
        assertApprovalBinding(context, plan, resolved);
        if (!resolved.approverId().isBlank() && !resolved.approverId().equals(context.approverId())) {
            throw new SecurityException("verified approval actor differs from the host-authenticated approver");
        }
        return resolved;
    }

    public PlanRevalidation revalidatePlan(CycleContext context) {
        try {
            requireCurrentUncertainty(context, clock.instant());
        } catch (IllegalStateException | SecurityException staleAssessment) {
            return new PlanRevalidation(RevalidationDisposition.STALE,
                    "uncertainty assessment is missing, stale, expired or no longer bound to this execution");
        }
        AuthorizedActionPlan plan = actionPlan(context);
        ApprovalBinding binding = binding(context, plan);
        if (context.approvalId().isBlank()
                || !authority.isApprovalStillValid(context, binding, context.approvalId(), clock.instant())) {
            return new PlanRevalidation(RevalidationDisposition.DENIED,
                    "approval is missing, revoked, expired or no longer bound to this exact action plan");
        }
        WorkingMemorySnapshot currentMemory = currentMemory(context);
        PlanningRequest fresh = Objects.requireNonNull(
                domain.revalidationRequest(context, decisionServices.selectedCandidate(context), currentMemory, clock.instant()),
                "domain adapter returned null revalidation request");
        var result = decisionServices.revalidateSelectedCandidate(context, fresh);
        return result.valid()
                ? new PlanRevalidation(RevalidationDisposition.VALID, result.reason())
                : new PlanRevalidation(RevalidationDisposition.STALE, result.reason());
    }

    public AuthorizedExecution executeAuthorizedCandidate(CycleContext context) {
        Instant now = clock.instant();
        try {
            requireCurrentUncertainty(context, now);
        } catch (IllegalStateException | SecurityException staleAssessment) {
            return new AuthorizedExecution(ExecutionDisposition.REJECTED, "uncertainty:" + context.executionId(), "",
                    "M3 dispatch refused because the scoped uncertainty assessment is missing or expired");
        }
        PlanRevalidation revalidation = revalidatePlan(context);
        if (revalidation.disposition() != RevalidationDisposition.VALID) {
            return new AuthorizedExecution(ExecutionDisposition.REJECTED, "revalidation:" + context.executionId(), "",
                    "M3 dispatch refused because final plan revalidation was not valid: " + revalidation.reason());
        }
        AuthorizedActionPlan plan = actionPlan(context);
        if (context.approvalId().isBlank()
                || !authority.isApprovalStillValid(context, binding(context, plan), context.approvalId(), now)) {
            return new AuthorizedExecution(ExecutionDisposition.REJECTED, batchId(plan), "",
                    "execution refused because the exact approval is not currently valid");
        }
        var principal = Objects.requireNonNull(authority.executionPrincipal(context, now), "authority returned null action principal");
        ActionExecutionBatch batch = actionBatches.start(plan, batchId(plan), principal.principalId(), now);
        if (batch.status() == ActionExecutionBatch.BatchStatus.SUCCEEDED) {
            return new AuthorizedExecution(ExecutionDisposition.SUCCEEDED, batch.batchId(), "action-batch:" + batch.batchId(),
                    "all M3 operations were already completed under their idempotency keys");
        }
        if (batch.status() == ActionExecutionBatch.BatchStatus.UNKNOWN_OUTCOME
                || batch.status() == ActionExecutionBatch.BatchStatus.PARTIAL) {
            return new AuthorizedExecution(ExecutionDisposition.UNKNOWN_OUTCOME, batch.batchId(), "action-batch:" + batch.batchId(),
                    "previous attempt may have partially changed the domain; reconcile instead of redispatching");
        }
        if (batch.status() == ActionExecutionBatch.BatchStatus.REJECTED) {
            return new AuthorizedExecution(ExecutionDisposition.REJECTED, batch.batchId(), "action-batch:" + batch.batchId(),
                    "the previous M3 attempt was rejected; do not replay the same plan automatically");
        }
        if (batch.status() == ActionExecutionBatch.BatchStatus.FAILED) {
            return new AuthorizedExecution(ExecutionDisposition.FAILED, batch.batchId(), "action-batch:" + batch.batchId(),
                    "the previous M3 attempt failed; a new approved plan is required before another attempt");
        }
        for (int index = 0; index < plan.commands().size(); index++) {
            if (batch.hasStep(index)) continue;
            ActionDispatchCommand<?, ?> command = plan.commands().get(index);
            String idempotencyKey = "cognitive:" + shortHash(plan.executionId() + "|" + plan.candidateSetId()
                    + "|" + plan.candidateId() + "|" + index + "|" + plan.fingerprint());
            ActionContext actionContext = ActionContext.create(context.executionId(), context.requestId(), principal,
                    clock.instant(), idempotencyKey);
            var result = command.dispatch(actionDispatcher, actionContext);
            ActionExecutionBatch.ActionExecutionItem item = executionItem(index, command, result);
            ActionExecutionBatch.BatchStatus nextStatus;
            if (result.status() == ActionStatus.SUCCEEDED || result.status() == ActionStatus.ALREADY_COMPLETED) {
                nextStatus = ActionExecutionBatch.BatchStatus.STARTED;
            } else if (result.status() == ActionStatus.REJECTED) {
                nextStatus = batch.hasSuccessfulEffect() ? ActionExecutionBatch.BatchStatus.PARTIAL : ActionExecutionBatch.BatchStatus.REJECTED;
            } else if (result.status() == ActionStatus.IN_PROGRESS
                    || result.failureOptional().map(f -> f.outcomeUnknown()).orElse(false)
                    || batch.hasSuccessfulEffect()) {
                nextStatus = ActionExecutionBatch.BatchStatus.UNKNOWN_OUTCOME;
            } else {
                nextStatus = ActionExecutionBatch.BatchStatus.FAILED;
            }
            batch = actionBatches.append(batch.batchId(), item, nextStatus, clock.instant());
            if (nextStatus != ActionExecutionBatch.BatchStatus.STARTED) {
                ExecutionDisposition disposition = nextStatus == ActionExecutionBatch.BatchStatus.REJECTED
                        ? ExecutionDisposition.REJECTED
                        : nextStatus == ActionExecutionBatch.BatchStatus.FAILED
                            ? ExecutionDisposition.FAILED : ExecutionDisposition.UNKNOWN_OUTCOME;
                return new AuthorizedExecution(disposition, batch.batchId(), "action-batch:" + batch.batchId(),
                        item.failureMessage().isBlank() ? "partial M3 execution requires reconciliation" : item.failureMessage());
            }
        }
        batch = actionBatches.complete(batch.batchId(), clock.instant());
        return new AuthorizedExecution(ExecutionDisposition.SUCCEEDED, batch.batchId(), "action-batch:" + batch.batchId(),
                "all selected actions completed through the M3 dispatcher");
    }

    public ExternalObservation observeAndReconcile(CycleContext context) {
        String batchId = required(context.executionOperationId(), "executionOperationId");
        ActionExecutionBatch batch = actionBatches.find(batchId).orElseThrow(() ->
                new IllegalStateException("execution batch is missing; external actions will not be repeated"));
        if (!batch.executionId().equals(context.executionId())) throw new SecurityException("execution batch belongs to another execution");
        ExternalObservation observation = Objects.requireNonNull(
                domain.observeAndReconcile(context, batch, clock.instant()), "domain reconciliation returned null observation");
        observations.save(observation);
        return observation;
    }

    public WorkingMemoryUpdated updateWorkingMemoryFromObservation(CycleContext context, ExternalObservation observation) {
        Objects.requireNonNull(observation, "observation");
        observations.save(observation);
        return writeObservation(context, observation);
    }

    public WorkingMemoryUpdated updateWorkingMemoryFromStoredObservation(CycleContext context) {
        String observationId = required(context.observationId(), "observationId");
        ExternalObservation observation = observations.find(observationId).orElseThrow(() ->
                new IllegalStateException("persisted observation is unavailable; action execution will not be repeated"));
        return writeObservation(context, observation);
    }

    public EpisodeRecorded recordOutcomeEpisode(CycleContext context) {
        String observationId = required(context.observationId(), "observationId");
        ExternalObservation observation = observations.find(observationId).orElseThrow(() ->
                new IllegalStateException("cannot record an outcome episode without the reconciled observation"));
        if (observation.disposition() != ObservationDisposition.OBJECTIVE_SATISFIED) {
            throw new SecurityException("only an objective-satisfied observation may complete the B4 intention and record a successful outcome");
        }
        Instant now = clock.instant();
        EpisodeDraft draft = Objects.requireNonNull(domain.outcomeEpisode(context, observation, now), "episode draft");
        RecoveryIntention activeIntention = currentIntention(context);
        var proof = Objects.requireNonNull(domain.objectiveSatisfactionProof(context, observation, now),
                "authoritative objective-satisfaction proof");
        EvidenceReference observationEvidence = new EvidenceReference("observation", observationId);
        if (!proof.objectiveFingerprint().equals(activeIntention.objectiveFingerprint())
                || !proof.evidence().contains(observationEvidence)
                || proof.verifiedAt().isAfter(now)) {
            throw new SecurityException("objective-satisfaction proof does not match the protected objective and reconciled observation");
        }
        MemoryScopeId scope = requireScope(context);
        if (!draft.provenance().source().equals(dev.supplychain.agent.memory.common.ProvenanceSource.AUTHORITATIVE_SYSTEM)
                && !draft.provenance().source().equals(dev.supplychain.agent.memory.common.ProvenanceSource.HUMAN_VERIFIED)
                && !draft.provenance().source().equals(dev.supplychain.agent.memory.common.ProvenanceSource.SIMULATOR)
                && !draft.provenance().source().equals(dev.supplychain.agent.memory.common.ProvenanceSource.VERIFIED_DOCUMENT)) {
            throw new SecurityException("only verified system, human, simulator or document provenance may create outcome episodes");
        }
        EvidenceReference idempotencyReference = observationEvidence;
        var episode = episodicMemory.recordIdempotent(scope, draft.title(), draft.summary(), draft.outcome(),
                draft.startedAt(), draft.completedAt(), now, draft.entities(), draft.events(), draft.evidence(),
                draft.provenance(), idempotencyReference);
        if (activeIntention.status() == IntentionStatus.ACTIVE) {
            if (activeIntention.version() != context.intentionVersion()) {
                throw new IllegalStateException("B4 intention version changed before objective completion; reload the context");
            }
            intentions.complete(activeIntention.id(), activeIntention.version(), proof, now);
        } else if (activeIntention.status() == IntentionStatus.COMPLETED) {
            if (!proof.equals(activeIntention.completionProof())) {
                throw new SecurityException("completed B4 intention has a different objective-satisfaction proof");
            }
        } else {
            throw new IllegalStateException("objective cannot be marked complete while B4 intention is " + activeIntention.status());
        }
        return new EpisodeRecorded(episode.id().value(), EpisodeVerificationStatus.RECORDED_UNVERIFIED);
    }

    public void recordEscalation(CycleContext context, String reason) {
        domain.recordEscalation(context, required(reason, "reason"), clock.instant());
    }

    private void validateBudgetedActionBindings() {
        if (resourceBudgetGuard == null) {
            if (actionDispatcher.hasExecutionBudget()) {
                throw new SecurityException(
                        "M3 external action dispatch cannot use a separate B6 guard when the lifecycle composition has no shared guard");
            }
            return;
        }
        if (!actionDispatcher.isBoundToBudgetIdentity(resourceBudgetGuard)) {
            throw new SecurityException(
                    "M3 external action dispatch must use the exact shared B6 execution guard");
        }
        actionRegistry.findHandler(ReasoningAction.ID).ifPresent(handler -> {
            if (!(handler instanceof ReasoningAction reasoning)
                    || !reasoning.isBoundToBudgetIdentity(resourceBudgetGuard)) {
                throw new SecurityException(
                        "registered M3 reasoning action must use the exact shared B6 execution guard");
            }
        });
    }

    private AuthorizedActionPlan actionPlan(CycleContext context) {
        // ActionRegistry is intentionally mutable during host startup. Revalidate at each
        // authorization/dispatch boundary so a later registration cannot bypass B6 composition.
        validateBudgetedActionBindings();
        var selected = decisionServices.selectedCandidate(context);
        if (context.intentionId().isBlank()) throw new SecurityException("M3 action plan is not bound to a persistent B4 intention");
        var commands = List.copyOf(domain.actionCommands(context, selected, clock.instant()));
        if (commands.size() != selected.candidate().actions().size()) {
            throw new SecurityException("domain adapter must provide exactly one M3 command for every selected planning action");
        }
        var descriptors = new ArrayList<ActionDescriptor>();
        var fingerprints = new ArrayList<String>();
        for (int index = 0; index < commands.size(); index++) {
            var command = commands.get(index);
            var planned = selected.candidate().actions().get(index);
            if (!command.planningActionId().equals(planned.planningActionId())
                    || !command.actionId().equals(planned.descriptorId())) {
                throw new SecurityException("M3 command order or action identity differs from the selected M5 candidate");
            }
            ActionDescriptor registered = actionRegistry.descriptor(command.actionId());
            ActionDescriptor plannedDescriptor = selected.request().actionDefinitionsById().get(command.actionId());
            if (!registered.equals(plannedDescriptor) || registered.effect() != ActionEffect.DOMAIN_WRITE
                    || registered.category() != dev.supplychain.agent.actions.api.ActionCategory.GROUNDING) {
                throw new SecurityException("registered M3 action differs from the trusted M5 grounding definition");
            }
            descriptors.add(registered);
            fingerprints.add(command.planningActionId() + ":" + command.actionId().value() + "="
                    + actionRegistry.requestFingerprint(command.actionId(), command.input()));
        }
        String canonical = selected.candidateSetId() + "|" + selected.candidate().candidateId()
                + "|" + context.intentionId() + "|" + context.intentionVersion()
                + "|" + String.join("|", fingerprints);
        String fingerprint = sha256(canonical);
        var plan = new AuthorizedActionPlan(context.executionId(), selected.candidateSetId(),
                selected.candidate().candidateId(), context.intentionId(), context.intentionVersion(),
                fingerprint, selected.candidate(), selected.request(), commands, descriptors, true);
        actionPlans.save(plan);
        return actionPlans.find(context.executionId(), selected.candidateSetId(), selected.candidate().candidateId())
                .orElseThrow(() -> new IllegalStateException("authorized action plan was not persisted"));
    }

    private ApprovalBinding binding(CycleContext context, AuthorizedActionPlan plan) {
        return new ApprovalBinding(context.requestId(), context.executionId(), context.memoryScopeId(),
                plan.candidateSetId(), plan.candidateId(), plan.intentionId(), plan.intentionVersion(), plan.fingerprint());
    }

    private static void assertApprovalBinding(CycleContext context, AuthorizedActionPlan plan, ApprovalResolved resolved) {
        if (!resolved.approvalId().equals(context.approvalId())
                || !resolved.candidateId().equals(plan.candidateId())
                || !resolved.candidateSetId().equals(plan.candidateSetId())
                || !resolved.intentionId().equals(plan.intentionId())
                || resolved.intentionVersion() != plan.intentionVersion()) {
            throw new SecurityException("approval response is not bound to the exact selected M5 candidate and B4 intention version");
        }
    }

    private ActionExecutionBatch.ActionExecutionItem executionItem(int index, ActionDispatchCommand<?, ?> command,
                                                                    dev.supplychain.agent.actions.api.ActionResult<?> result) {
        if (result.status() == ActionStatus.SUCCEEDED || result.status() == ActionStatus.ALREADY_COMPLETED) {
            var receipt = result.receiptOptional().orElseThrow();
            return new ActionExecutionBatch.ActionExecutionItem(index, command.actionId().value(), result.status(),
                    receipt.operationId(), receipt.resultReference(), "", "", false);
        }
        var failure = result.failureOptional().orElseThrow();
        return new ActionExecutionBatch.ActionExecutionItem(index, command.actionId().value(), result.status(),
                "", "", failure.code().name(), failure.message(), failure.outcomeUnknown());
    }

    private WorkingMemoryUpdated writeObservation(CycleContext context, ExternalObservation observation) {
        if (!observation.observationId().equals(context.observationId()) && !context.observationId().isBlank()) {
            throw new SecurityException("observation update does not match the observation reference in graph state");
        }
        try {
            WorkingMemorySnapshot current = currentMemory(context);
            if (current.version() != context.workingMemoryVersion()) {
                return new WorkingMemoryUpdated(WorkingMemoryUpdateDisposition.CONFLICT, current.id().value(), current.version(),
                        "working memory advanced from graph version " + context.workingMemoryVersion() + " to " + current.version());
            }
            String key = OBSERVATION_KEY_PREFIX + observation.observationId();
            var value = new MemoryValue.Text(observation.disposition() + "|" + observation.reason());
            var evidence = List.of(new EvidenceReference("observation", observation.observationId()));
            var existing = current.values().get(key);
            if (existing != null && existing.value().equals(value) && existing.evidence().equals(evidence)) {
                return new WorkingMemoryUpdated(WorkingMemoryUpdateDisposition.UPDATED,
                        current.id().value(), current.version(), "observation was already applied idempotently");
            }
            var updated = workingMemory.put(current.id(), context.workingMemoryVersion(), key, value, evidence, clock.instant());
            return new WorkingMemoryUpdated(WorkingMemoryUpdateDisposition.UPDATED,
                    updated.id().value(), updated.version(), "persisted reconciled observation in CoALA working memory");
        } catch (dev.supplychain.agent.memory.common.MemoryVersionConflictException conflict) {
            return new WorkingMemoryUpdated(WorkingMemoryUpdateDisposition.CONFLICT,
                    context.workingMemoryId(), context.workingMemoryVersion(), safeMessage(conflict));
        } catch (RuntimeException failure) {
            return new WorkingMemoryUpdated(WorkingMemoryUpdateDisposition.FAILED,
                    context.workingMemoryId(), context.workingMemoryVersion(), safeMessage(failure));
        }
    }

    private WorkingMemorySnapshot applyContextFacts(CycleContext context, WorkingMemorySnapshot memory, Instant at) {
        return applyFacts(context, memory, domain.contextFacts(context, memory, at), at);
    }

    private WorkingMemorySnapshot applyFacts(CycleContext context, WorkingMemorySnapshot memory,
                                             List<CognitiveCycleLifecyclePort.ContextFact> facts, Instant at) {
        if (!memory.scopeId().equals(requireScope(context))) throw new SecurityException("context fact scope mismatch");
        WorkingMemorySnapshot current = memory;
        for (var fact : facts) {
            var existing = current.values().get(fact.key());
            if (existing != null && existing.value().equals(fact.value()) && existing.evidence().equals(fact.evidence())) continue;
            current = workingMemory.put(current.id(), current.version(), fact.key(), fact.value(), fact.evidence(), at);
        }
        return current;
    }

    private WorkingMemorySnapshot currentMemory(CycleContext context) {
        if (context.workingMemoryId().isBlank()) throw new IllegalStateException("working memory has not been loaded for this cycle");
        WorkingMemorySnapshot memory = workingMemory.load(new WorkingMemoryId(context.workingMemoryId()));
        if (!memory.scopeId().equals(requireScope(context))) throw new SecurityException("working memory scope differs from active cycle scope");
        return memory;
    }

    private RecoveryIntention currentIntention(CycleContext context) {
        RecoveryIntention intention = intentions.get(new RecoveryIntentionId(required(context.intentionId(), "intentionId")));
        if (!intention.scopeId().equals(requireScope(context))) throw new SecurityException("intention scope differs from active cycle scope");
        return intention;
    }

    private MemoryScopeId requireScope(CycleContext context) {
        MemoryScopeId scope = Objects.requireNonNull(domain.memoryScope(context), "domain returned null memory scope");
        if (!scope.value().equals(context.memoryScopeId())) throw new SecurityException("domain memory scope differs from graph memory scope");
        return scope;
    }

    private static String batchId(AuthorizedActionPlan plan) {
        return "action-batch:" + sha256(plan.executionId() + "|" + plan.candidateSetId() + "|"
                + plan.candidateId() + "|" + plan.fingerprint());
    }

    /**
     * Makes a logical refresh trigger idempotent only for the exact predecessor revision.
     * A checkpoint replay carries the same predecessor and therefore reuses the same host key;
     * a later cycle may encounter the same logical trigger after the assessment has advanced,
     * and must then create the next revision rather than receive an older cached result.
     */
    private static String uncertaintyAssessmentRequestKey(
            String triggerId, String executionId, String memoryScopeId,
            UncertaintyAssessment.Reference previous) {
        String predecessor = previous == null
                ? "initial"
                : previous.assessmentId().length() + ":" + previous.assessmentId()
                        + "|" + previous.version()
                        + "|" + previous.executionId().length() + ":" + previous.executionId()
                        + "|" + previous.memoryScopeId().length() + ":" + previous.memoryScopeId();
        String canonical = "uncertainty-assessment-request:v1|"
                + triggerId.length() + ":" + triggerId
                + "|" + executionId.length() + ":" + executionId
                + "|" + memoryScopeId.length() + ":" + memoryScopeId
                + "|" + predecessor;
        return "uncertainty-assessment:v1:" + sha256(canonical);
    }

    private static String shortHash(String canonical) { return sha256(canonical).substring(0, 32); }
    private static String sha256(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }
    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
