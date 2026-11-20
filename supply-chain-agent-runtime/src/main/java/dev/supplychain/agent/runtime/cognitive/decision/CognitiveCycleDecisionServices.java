package dev.supplychain.agent.runtime.cognitive.decision;

import dev.supplychain.agent.memory.retrieval.MemoryRetrievalService;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryVersionConflictException;
import dev.supplychain.agent.memory.working.MemoryValue;
import dev.supplychain.agent.memory.working.WorkingMemoryId;
import dev.supplychain.agent.memory.working.WorkingMemoryService;
import dev.supplychain.agent.memory.working.WorkingMemorySnapshot;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateFactory;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidate;
import dev.supplychain.agent.mechanisms.b1.B1RetrievalSelector;
import dev.supplychain.agent.mechanisms.b1.B1SelectionResult;
import dev.supplychain.agent.mechanisms.b3.WorkspaceAdmissionService;
import dev.supplychain.agent.mechanisms.b3.WorkspaceAdmissionStatus;
import dev.supplychain.agent.mechanisms.b3.WorkspaceBroadcastService;
import dev.supplychain.agent.planning.api.CandidateEvaluator;
import dev.supplychain.agent.planning.api.PlanningAction;
import dev.supplychain.agent.planning.api.PlanCandidate;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningResult;
import dev.supplychain.agent.planning.api.PlanningState;
import dev.supplychain.agent.planning.api.PlanningWorldModel;
import dev.supplychain.agent.planning.api.PlannerTerminationReason;
import dev.supplychain.agent.planning.api.WorldTransition;
import dev.supplychain.agent.planning.routing.PlannerRouter;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;
import dev.supplychain.agent.runtime.cognitive.ResourceBudgetExecutionGuard;
import dev.supplychain.agent.mechanisms.b6.ResourceBudgetExceededException;
import dev.supplychain.agent.mechanisms.b6.ResourceUnit;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations.CandidateEvaluation;
import static dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations.EvaluationDisposition;
import static dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations.EvidenceDisposition;
import static dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations.EvidenceRetrieved;
import static dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations.PlanningPrepared;
import static dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations.WorkspaceDisposition;
import static dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations.WorkspacePrepared;

/**
 * Real service composition for the decision side of the CoALA cycle.
 *
 * <p>Calls M2 retrieval, B1 activation/utility selection, B3 bounded workspace admission and
 * broadcast, M5 planner routing, and the common deterministic evaluator. Domain conversion and
 * evidence/artifact persistence remain explicit ports rather than graph-state payloads.</p>
 */
public final class CognitiveCycleDecisionServices {
    /** Stable active-context slot containing references, never copied source documents. */
    public static final String COALA_RETRIEVED_MEMORY_REFERENCES_KEY = "coala.retrieval.selected.references";

    private final MemoryRetrievalService memoryRetrieval;
    private final WorkingMemoryService workingMemory;
    private final B1MemoryCandidateFactory b1CandidateFactory;
    private final B1RetrievalSelector b1Selector;
    private final WorkspaceAdmissionService workspaceAdmission;
    private final WorkspaceBroadcastService workspaceBroadcast;
    private final PlannerRouter plannerRouter;
    private final PlanningWorldModel planningWorldModel;
    private final CandidateEvaluator candidateEvaluator;
    private final PlanningArtifactStore planningArtifacts;
    private final CognitiveCycleDecisionPort domainPort;
    private final Clock clock;
    private final ResourceBudgetExecutionGuard budgetGuard;

    public CognitiveCycleDecisionServices(
            MemoryRetrievalService memoryRetrieval,
            B1MemoryCandidateFactory b1CandidateFactory,
            B1RetrievalSelector b1Selector,
            WorkspaceAdmissionService workspaceAdmission,
            WorkspaceBroadcastService workspaceBroadcast,
            PlannerRouter plannerRouter,
            PlanningWorldModel planningWorldModel,
            CandidateEvaluator candidateEvaluator,
            PlanningArtifactStore planningArtifacts,
            CognitiveCycleDecisionPort domainPort,
            Clock clock) {
        this(memoryRetrieval, b1CandidateFactory, b1Selector, workspaceAdmission, workspaceBroadcast,
                plannerRouter, planningWorldModel, candidateEvaluator, planningArtifacts, domainPort, clock, null, null);
    }

    /** Production composition constructor with the shared durable B6 execution guard. */
    public CognitiveCycleDecisionServices(
            MemoryRetrievalService memoryRetrieval,
            B1MemoryCandidateFactory b1CandidateFactory,
            B1RetrievalSelector b1Selector,
            WorkspaceAdmissionService workspaceAdmission,
            WorkspaceBroadcastService workspaceBroadcast,
            PlannerRouter plannerRouter,
            PlanningWorldModel planningWorldModel,
            CandidateEvaluator candidateEvaluator,
            PlanningArtifactStore planningArtifacts,
            CognitiveCycleDecisionPort domainPort,
            Clock clock,
            ResourceBudgetExecutionGuard budgetGuard) {
        this(memoryRetrieval, b1CandidateFactory, b1Selector, workspaceAdmission, workspaceBroadcast,
                plannerRouter, planningWorldModel, candidateEvaluator, planningArtifacts, domainPort,
                clock, budgetGuard, null);
    }

    /** Full composition shares the exact working-memory service used by lifecycle operations. */
    public CognitiveCycleDecisionServices(
            MemoryRetrievalService memoryRetrieval,
            B1MemoryCandidateFactory b1CandidateFactory,
            B1RetrievalSelector b1Selector,
            WorkspaceAdmissionService workspaceAdmission,
            WorkspaceBroadcastService workspaceBroadcast,
            PlannerRouter plannerRouter,
            PlanningWorldModel planningWorldModel,
            CandidateEvaluator candidateEvaluator,
            PlanningArtifactStore planningArtifacts,
            CognitiveCycleDecisionPort domainPort,
            Clock clock,
            ResourceBudgetExecutionGuard budgetGuard,
            WorkingMemoryService workingMemory) {
        this.memoryRetrieval = Objects.requireNonNull(memoryRetrieval, "memoryRetrieval");
        this.workingMemory = workingMemory;
        this.b1CandidateFactory = Objects.requireNonNull(b1CandidateFactory, "b1CandidateFactory");
        this.b1Selector = Objects.requireNonNull(b1Selector, "b1Selector");
        this.workspaceAdmission = Objects.requireNonNull(workspaceAdmission, "workspaceAdmission");
        this.workspaceBroadcast = Objects.requireNonNull(workspaceBroadcast, "workspaceBroadcast");
        this.plannerRouter = Objects.requireNonNull(plannerRouter, "plannerRouter");
        this.planningWorldModel = Objects.requireNonNull(planningWorldModel, "planningWorldModel");
        this.candidateEvaluator = Objects.requireNonNull(candidateEvaluator, "candidateEvaluator");
        this.planningArtifacts = Objects.requireNonNull(planningArtifacts, "planningArtifacts");
        this.domainPort = Objects.requireNonNull(domainPort, "domainPort");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.budgetGuard = budgetGuard;
    }

    public ResourceBudgetExecutionGuard resourceBudgetGuard() { return budgetGuard; }

    public EvidenceRetrieved retrieveEvidence(CognitiveCycleOperations.CycleContext context) {
        Objects.requireNonNull(context, "context");
        Instant at = clock.instant();
        var scope = domainPort.memoryScope(context);
        var query = domainPort.retrievalQuery(context, at);
        var selectionRequest = domainPort.b1SelectionRequest(context, at);
        if (!scope.equals(query.scopeId()) || !scope.equals(selectionRequest.scopeId())) {
            throw new SecurityException("memory retrieval and B1 scopes must match the active cognitive-cycle scope");
        }
        var retrieved = memoryRetrieval.retrieve(query);
        List<B1MemoryCandidate> candidates = b1CandidateFactory.from(
                scope, retrieved, query.knownAt(), domainPort.b1FeatureProvider(context));
        B1SelectionResult selected = b1Selector.select(selectionRequest, candidates);
        EvidenceRetrieved persisted = Objects.requireNonNull(
                domainPort.persistEvidenceSelection(context, retrieved, selected, at),
                "domain port returned null evidence result");
        if (persisted.disposition() == EvidenceDisposition.READY && persisted.evidenceSetId().isBlank()) {
            throw new IllegalStateException("ready evidence result must reference the persisted evidence set");
        }
        if (persisted.disposition() != EvidenceDisposition.READY) return persisted;

        WorkingMemorySnapshot projected;
        try {
            projected = projectSelectedMemoryReferences(context, scope, candidates,
                    selected.selectedCandidates(), at);
        } catch (RuntimeException projectionFailure) {
            return new EvidenceRetrieved(EvidenceDisposition.FAILED, persisted.evidenceSetId(),
                    Math.max(persisted.maximumUncertainty(), 1.0),
                    "CoALA retrieval-to-working-memory projection failed closed: "
                            + safeMessage(projectionFailure), context.workingMemoryId(), context.workingMemoryVersion());
        }
        if (projected == null) return new EvidenceRetrieved(persisted.disposition(), persisted.evidenceSetId(),
                persisted.maximumUncertainty(), persisted.reason(), context.workingMemoryId(), context.workingMemoryVersion());
        return new EvidenceRetrieved(persisted.disposition(), persisted.evidenceSetId(),
                persisted.maximumUncertainty(), persisted.reason() + "; selected long-term memory references projected to CoALA working memory",
                projected.id().value(), projected.version());
    }

    /**
     * Projects selected M2 long-term memories into the active CoALA working-memory snapshot.
     * Only stable, scope-bound references are copied; source records remain in their authoritative
     * episodic/semantic/procedural repositories. A stale graph version is never merged silently.
     */
    private WorkingMemorySnapshot projectSelectedMemoryReferences(
            CognitiveCycleOperations.CycleContext context,
            dev.supplychain.agent.memory.common.MemoryScopeId expectedScope,
            List<B1MemoryCandidate> retrievedCandidates,
            List<B1MemoryCandidate> selectedCandidates,
            Instant at) {
        var selectedById = new java.util.HashMap<String, B1MemoryCandidate>();
        for (B1MemoryCandidate candidate : retrievedCandidates) {
            selectedById.put(candidate.candidateId(), candidate);
        }
        var longTerm = new ArrayList<B1MemoryCandidate>();
        for (B1MemoryCandidate selected : selectedCandidates) {
            if (!expectedScope.equals(selected.scopeId())) {
                throw new SecurityException("selected B1 memory candidate belongs to a different scope");
            }
            if (!selected.equals(selectedById.get(selected.candidateId()))) {
                throw new SecurityException("B1 selected a memory candidate that was not returned by M2 retrieval");
            }
            if (selected.kind() != dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateKind.WORKING) {
                longTerm.add(selected);
            }
        }
        longTerm.sort(Comparator.comparing((B1MemoryCandidate item) -> item.kind().name())
                .thenComparing(B1MemoryCandidate::memoryReference));

        // Existing callers that only exercise empty retrieval can use the compatibility
        // constructor. The complete application host always supplies the shared service.
        if (workingMemory == null) {
            if (!longTerm.isEmpty()) {
                throw new IllegalStateException("WorkingMemoryService is required when B1 selects long-term memory");
            }
            return null;
        }
        if (context.workingMemoryId().isBlank()) {
            if (!longTerm.isEmpty()) {
                throw new IllegalStateException("active working-memory identity is required to project selected memory");
            }
            return null;
        }

        WorkingMemoryId id = new WorkingMemoryId(context.workingMemoryId());
        WorkingMemorySnapshot current = workingMemory.load(id);
        if (!expectedScope.equals(current.scopeId())) {
            throw new SecurityException("active working memory belongs to a different scope");
        }
        if (!current.expiresAt().isAfter(at)) {
            throw new IllegalStateException("expired working memory cannot receive retrieved references");
        }

        String projectionMarker = "@projection:v1|execution="
                + require(context.executionId(), "executionId")
                + "|basedOnWorkingMemoryVersion=" + context.workingMemoryVersion();
        var references = new ArrayList<String>();
        references.add(projectionMarker);
        longTerm.stream().map(B1MemoryCandidate::memoryReference).forEach(references::add);
        List<EvidenceReference> evidence = longTerm.stream()
                .map(item -> new EvidenceReference("coala-selected-memory", item.memoryReference()))
                .toList();
        MemoryValue.TextList value = new MemoryValue.TextList(references);
        var existing = current.values().get(COALA_RETRIEVED_MEMORY_REFERENCES_KEY);
        boolean alreadyProjected = existing != null && existing.value().equals(value)
                && existing.evidence().equals(evidence);

        if (current.version() != context.workingMemoryVersion()) {
            if (current.version() == context.workingMemoryVersion() + 1L && alreadyProjected
                    && existing.updatedAt().equals(at)) {
                // The single version increment and exact operation timestamp identify the prior
                // projection CAS after a graph-checkpoint interruption. Any later concurrent
                // write changes the version again and is deliberately not treated as a replay.
                return current;
            }
            throw new MemoryVersionConflictException("working-memory version changed during retrieval projection: current="
                    + current.version() + ", expected=" + context.workingMemoryVersion());
        }
        if (longTerm.isEmpty() && existing == null) return current;
        if (alreadyProjected) return current;
        return workingMemory.put(id, context.workingMemoryVersion(), COALA_RETRIEVED_MEMORY_REFERENCES_KEY,
                value, evidence, at);
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    public WorkspacePrepared prepareWorkspace(CognitiveCycleOperations.CycleContext context) {
        Objects.requireNonNull(context, "context");
        Instant at = clock.instant();
        UncertaintyAssessment activeUncertainty = requireCurrentUncertainty(context, at);
        if (context.evidenceSetId().isBlank()) {
            throw new IllegalStateException("B3 workspace admission requires the persisted B1 evidence-set reference");
        }
        var request = domainPort.workspaceAdmissionRequest(context, at);
        if (!Objects.equals(request.uncertaintyAssessmentReference(), activeUncertainty.reference())) {
            throw new SecurityException("B3 admission request must carry the exact current uncertainty assessment reference");
        }
        var expectedScope = domainPort.memoryScope(context);
        if (!expectedScope.equals(request.scopeId())) {
            throw new SecurityException("workspace scope does not match the active cognitive-cycle scope");
        }
        var outcome = workspaceAdmission.admit(request);
        if (outcome.status() == WorkspaceAdmissionStatus.INSUFFICIENT_MANDATORY_CAPACITY) {
            return new WorkspacePrepared(WorkspaceDisposition.INSUFFICIENT_CAPACITY, "", outcome.explanation());
        }

        var snapshot = outcome.snapshotOptional().orElseThrow();
        var consumers = domainPort.workspaceConsumers(context);
        if (consumers.isEmpty()) {
            return new WorkspacePrepared(WorkspaceDisposition.FAILED, snapshot.id(),
                    "workspace was admitted but no downstream consumers were configured; broadcast was not attempted");
        }
        var report = workspaceBroadcast.broadcast(snapshot, expectedScope, consumers, at);
        domainPort.recordWorkspaceBroadcast(context, report, at);
        if (!report.allAccepted()) {
            String failed = report.receipts().stream()
                    .filter(receipt -> receipt.status() != dev.supplychain.agent.mechanisms.b3.ConsumerReceiptStatus.ACCEPTED)
                    .map(receipt -> receipt.consumerId() + "=" + receipt.status())
                    .sorted().reduce((left, right) -> left + ", " + right).orElse("unknown consumer failure");
            return new WorkspacePrepared(WorkspaceDisposition.FAILED, snapshot.id(),
                    "workspace admission succeeded but broadcast was not fully acknowledged: " + failed);
        }
        if (outcome.status() == WorkspaceAdmissionStatus.INCOMPLETE_COVERAGE) {
            return new WorkspacePrepared(WorkspaceDisposition.INCOMPLETE, snapshot.id(), outcome.explanation());
        }
        return new WorkspacePrepared(WorkspaceDisposition.ADMITTED, snapshot.id(),
                outcome.explanation() + "; all configured consumers acknowledged the workspace");
    }

    public PlanningPrepared proposeCandidates(CognitiveCycleOperations.CycleContext context) {
        Objects.requireNonNull(context, "context");
        Instant at = clock.instant();
        UncertaintyAssessment activeUncertainty = requireCurrentUncertainty(context, at);
        PlanningRequest request = Objects.requireNonNull(
                domainPort.planningRequest(context, at), "domain port returned null planning request");
        if (!Objects.equals(request.uncertaintyAssessmentReference(), activeUncertainty.reference())) {
            throw new SecurityException("M5 planning request must carry the exact current uncertainty assessment reference");
        }
        if (!request.executionId().equals(context.executionId())) {
            throw new SecurityException("planning request execution identity does not match graph execution");
        }
        if (context.evidenceSetId().isBlank() || context.workspaceSnapshotId().isBlank()) {
            throw new IllegalStateException("M5 planning requires persisted evidence and an admitted workspace snapshot");
        }
        if (!request.workingMemory().id().value().equals(context.workingMemoryId())
                || !request.workingMemory().scopeId().equals(domainPort.memoryScope(context))) {
            throw new SecurityException("planning request working-memory identity or scope does not match the active cognitive cycle");
        }
        if (request.workingMemory().version() != context.workingMemoryVersion()) {
            throw new IllegalStateException("planning request was built from a stale working-memory version");
        }
        if (!request.initialWorld().recoveryObjectives().containsKey(request.objective().id())) {
            throw new SecurityException("planning objective is not present in the authoritative world snapshot");
        }
        PlanningResult result;
        if (budgetGuard == null) {
            result = plannerRouter.plan(request);
        } else {
            var plannerBudget = request.budget();
            ResourceUsage reserved = new ResourceUsage(Map.of(
                    ResourceUnit.SEARCH_EXPANSIONS, (long) plannerBudget.maxExpansions(),
                    ResourceUnit.REASONING_EVALUATIONS, (long) plannerBudget.maxEvaluations(),
                    ResourceUnit.MODEL_CALLS, (long) plannerBudget.maxModelCalls(),
                    ResourceUnit.MCTS_ROLLOUTS, (long) plannerBudget.maxRollouts()));
            try {
                result = budgetGuard.execute(context.executionId(), "m5-planner:" + request.fingerprint(), reserved,
                        () -> plannerRouter.plan(request), planned -> new ResourceUsage(Map.of(
                                ResourceUnit.SEARCH_EXPANSIONS, planned.statistics().expansions(),
                                ResourceUnit.REASONING_EVALUATIONS, planned.statistics().evaluations(),
                                ResourceUnit.MODEL_CALLS, planned.statistics().modelCalls(),
                                ResourceUnit.MCTS_ROLLOUTS, planned.statistics().rollouts())));
            } catch (ResourceBudgetExceededException exhausted) {
                var selection = plannerRouter.select(request);
                result = new PlanningResult(selection.plannerId(), List.of(), PlannerTerminationReason.RESOURCE_BUDGET_EXHAUSTED,
                        new dev.supplychain.agent.planning.api.PlanningStatistics(0, 0, 0, 0, 0, 0, Duration.ZERO),
                        "budget-blocked-" + request.fingerprint().substring(0, 12),
                        selection.explanation() + "; durable B6 reservation refused: " + exhausted.getMessage(), List.of());
            }
        }
        String candidateSetId = planningArtifacts.store(request, result, clock.instant());
        return new PlanningPrepared(result.plannerId(), result.sessionId(), candidateSetId,
                result.candidates().size(), result.terminationReason());
    }

    public CandidateEvaluation evaluateCandidates(CognitiveCycleOperations.CycleContext context) {
        Objects.requireNonNull(context, "context");
        UncertaintyAssessment activeUncertainty = requireCurrentUncertainty(context, clock.instant());
        String candidateSetId = require(context.candidateSetId(), "candidateSetId");
        var artifacts = planningArtifacts.find(candidateSetId).orElseThrow(() ->
                new IllegalStateException("planner candidate set is not available: " + candidateSetId));
        if (!artifacts.executionId().equals(context.executionId())) {
            throw new SecurityException("candidate set belongs to a different graph execution");
        }
        PlanningRequest request = artifacts.request();
        if (!Objects.equals(request.uncertaintyAssessmentReference(), activeUncertainty.reference())) {
            throw new SecurityException("candidate set was planned under a different uncertainty assessment revision");
        }
        PlanningResult result = artifacts.result();
        if (!request.workingMemory().id().value().equals(context.workingMemoryId())
                || request.workingMemory().version() != context.workingMemoryVersion()
                || !request.workingMemory().scopeId().equals(domainPort.memoryScope(context))) {
            throw new SecurityException("candidate set is not bound to the active working-memory identity, version and scope");
        }
        if (!result.plannerId().name().equals(context.plannerId())
                || !result.sessionId().equals(context.plannerSessionId())) {
            throw new SecurityException("candidate set planner identity/session does not match the active graph state");
        }
        if (result.candidates().isEmpty()) {
            if (result.terminationReason() == PlannerTerminationReason.SEARCH_BUDGET_EXHAUSTED
                    || result.terminationReason() == PlannerTerminationReason.RESOURCE_BUDGET_EXHAUSTED) {
                return new CandidateEvaluation(EvaluationDisposition.NEEDS_MORE_SEARCH, "",
                        "planner exhausted its current search budget without a candidate: " + result.terminationReason());
            }
            return new CandidateEvaluation(EvaluationDisposition.NO_FEASIBLE_CANDIDATE, "",
                    "planner returned no candidate: " + result.terminationReason());
        }

        var evaluated = result.candidates().stream()
                .map(candidate -> evaluateCandidate(request, candidate.candidateId(), candidate.actions()))
                .filter(candidate -> candidate.feasible())
                .sorted(Comparator.comparingDouble(EvaluatedCandidate::score).reversed()
                        .thenComparing(EvaluatedCandidate::candidateId))
                .toList();
        if (evaluated.isEmpty()) {
            return new CandidateEvaluation(EvaluationDisposition.NO_FEASIBLE_CANDIDATE, "",
                    "all proposed candidates failed deterministic replay or hard-constraint evaluation");
        }
        EvaluatedCandidate best = evaluated.getFirst();
        return new CandidateEvaluation(EvaluationDisposition.SELECTED, best.candidateId(),
                "selected by common deterministic evaluation; feasible=" + evaluated.size()
                        + ", score=" + best.score() + ", evaluator=" + candidateEvaluator.configurationFingerprint());
    }

    private UncertaintyAssessment requireCurrentUncertainty(CognitiveCycleOperations.CycleContext context, Instant at) {
        var reference = context.requireUncertaintyAssessmentReference();
        UncertaintyAssessment assessment = Objects.requireNonNull(
                domainPort.currentUncertaintyAssessment(context, at), "decision domain returned null uncertainty assessment");
        if (!assessment.reference().equals(reference)
                || !assessment.isCurrentFor(context.executionId(), context.memoryScopeId(), at)) {
            throw new SecurityException("B3/M5 decision stage requires the exact current uncertainty assessment referenced by graph state");
        }
        return assessment;
    }

    /** Returns the exact selected M5 candidate and its immutable planning provenance. */
    public SelectedPlanningCandidate selectedCandidate(CognitiveCycleOperations.CycleContext context) {
        Objects.requireNonNull(context, "context");
        String candidateSetId = require(context.candidateSetId(), "candidateSetId");
        var artifacts = planningArtifacts.find(candidateSetId).orElseThrow(() ->
                new IllegalStateException("planner candidate set is not available: " + candidateSetId));
        if (!artifacts.executionId().equals(context.executionId())) {
            throw new SecurityException("candidate set belongs to a different graph execution");
        }
        if (!artifacts.result().plannerId().name().equals(context.plannerId())
                || !artifacts.result().sessionId().equals(context.plannerSessionId())) {
            throw new SecurityException("candidate set planner identity/session does not match the active graph state");
        }
        PlanCandidate candidate = artifacts.result().candidates().stream()
                .filter(value -> value.candidateId().equals(context.selectedCandidateId()))
                .findFirst().orElseThrow(() -> new SecurityException(
                        "selected candidate is not part of the active candidate set"));
        if (!candidate.objectiveId().equals(artifacts.request().objective().id())) {
            throw new SecurityException("selected candidate targets a different recovery objective");
        }
        return new SelectedPlanningCandidate(candidateSetId, candidate, artifacts.request(), artifacts.result());
    }

    /** Replays the already-selected action path against a freshly loaded authoritative world snapshot. */
    public CandidateRevalidation revalidateSelectedCandidate(
            CognitiveCycleOperations.CycleContext context, PlanningRequest freshRequest) {
        Objects.requireNonNull(freshRequest, "freshRequest");
        UncertaintyAssessment activeUncertainty = requireCurrentUncertainty(context, clock.instant());
        if (!Objects.equals(freshRequest.uncertaintyAssessmentReference(), activeUncertainty.reference())) {
            return new CandidateRevalidation(false, "fresh planning request does not carry the active uncertainty assessment revision");
        }
        SelectedPlanningCandidate selected = selectedCandidate(context);
        PlanningRequest original = selected.request();
        if (!freshRequest.executionId().equals(context.executionId())
                || !freshRequest.objective().id().equals(original.objective().id())
                || !freshRequest.objective().equals(original.objective())
                || !freshRequest.hardConstraints().containsAll(original.hardConstraints())
                || !freshRequest.workingMemory().id().value().equals(context.workingMemoryId())
                || freshRequest.workingMemory().version() != context.workingMemoryVersion()
                || !freshRequest.workingMemory().scopeId().equals(original.workingMemory().scopeId())) {
            return new CandidateRevalidation(false,
                    "fresh request changed execution, objective, hard constraints, or active working-memory binding");
        }
        if (!freshRequest.actionDefinitionsById().equals(original.actionDefinitionsById())) {
            return new CandidateRevalidation(false, "trusted grounding action definitions changed after planning");
        }
        EvaluatedCandidate replay = evaluateCandidate(freshRequest, selected.candidate().candidateId(),
                selected.candidate().actions());
        return replay.feasible()
                ? new CandidateRevalidation(true, "selected candidate remains feasible under fresh deterministic replay")
                : new CandidateRevalidation(false, "selected candidate no longer satisfies deterministic transitions and hard constraints");
    }

    public record SelectedPlanningCandidate(String candidateSetId, PlanCandidate candidate,
                                            PlanningRequest request, PlanningResult result) {
        public SelectedPlanningCandidate {
            candidateSetId = require(candidateSetId, "candidateSetId");
            Objects.requireNonNull(candidate, "candidate");
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(result, "result");
        }
    }

    public record CandidateRevalidation(boolean valid, String reason) {
        public CandidateRevalidation { reason = require(reason, "reason"); }
    }

    private EvaluatedCandidate evaluateCandidate(PlanningRequest request, String candidateId,
                                                  List<PlanningAction> actions) {
        PlanningState state = planningWorldModel.initialState(request);
        for (PlanningAction proposed : actions) {
            var trusted = planningWorldModel.availableActions(request, state).stream()
                    .filter(action -> action.planningActionId().equals(proposed.planningActionId()))
                    .filter(proposed::equals)
                    .findFirst();
            if (trusted.isEmpty()) {
                return new EvaluatedCandidate(candidateId, false, 0.0);
            }
            WorldTransition transition = planningWorldModel.apply(request, state, trusted.get());
            if (transition instanceof WorldTransition.Rejected) {
                return new EvaluatedCandidate(candidateId, false, 0.0);
            }
            state = ((WorldTransition.Applied) transition).state();
        }
        var evaluation = candidateEvaluator.evaluate(request, state);
        return new EvaluatedCandidate(candidateId, evaluation.feasible(), evaluation.score());
    }

    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }

    private record EvaluatedCandidate(String candidateId, boolean feasible, double score) { }
}
