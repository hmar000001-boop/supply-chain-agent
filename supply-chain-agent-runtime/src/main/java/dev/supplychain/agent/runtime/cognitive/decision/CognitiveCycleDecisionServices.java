package dev.supplychain.agent.runtime.cognitive.decision;

import dev.supplychain.agent.memory.retrieval.MemoryRetrievalService;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateFactory;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidate;
import dev.supplychain.agent.mechanisms.b1.B1RetrievalSelector;
import dev.supplychain.agent.mechanisms.b1.B1SelectionResult;
import dev.supplychain.agent.mechanisms.b3.WorkspaceAdmissionService;
import dev.supplychain.agent.mechanisms.b3.WorkspaceAdmissionStatus;
import dev.supplychain.agent.mechanisms.b3.WorkspaceBroadcastService;
import dev.supplychain.agent.planning.api.CandidateEvaluator;
import dev.supplychain.agent.planning.api.PlanningAction;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningResult;
import dev.supplychain.agent.planning.api.PlanningState;
import dev.supplychain.agent.planning.api.PlanningWorldModel;
import dev.supplychain.agent.planning.api.PlannerTerminationReason;
import dev.supplychain.agent.planning.api.WorldTransition;
import dev.supplychain.agent.planning.routing.PlannerRouter;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
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
    private final MemoryRetrievalService memoryRetrieval;
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
        this.memoryRetrieval = Objects.requireNonNull(memoryRetrieval, "memoryRetrieval");
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
    }

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
        return persisted;
    }

    public WorkspacePrepared prepareWorkspace(CognitiveCycleOperations.CycleContext context) {
        Objects.requireNonNull(context, "context");
        if (context.evidenceSetId().isBlank()) {
            throw new IllegalStateException("B3 workspace admission requires the persisted B1 evidence-set reference");
        }
        Instant at = clock.instant();
        var request = domainPort.workspaceAdmissionRequest(context, at);
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
        PlanningRequest request = Objects.requireNonNull(
                domainPort.planningRequest(context, clock.instant()), "domain port returned null planning request");
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
        PlanningResult result = plannerRouter.plan(request);
        String candidateSetId = planningArtifacts.store(request, result, clock.instant());
        return new PlanningPrepared(result.plannerId(), result.sessionId(), candidateSetId,
                result.candidates().size(), result.terminationReason());
    }

    public CandidateEvaluation evaluateCandidates(CognitiveCycleOperations.CycleContext context) {
        Objects.requireNonNull(context, "context");
        String candidateSetId = require(context.candidateSetId(), "candidateSetId");
        var artifacts = planningArtifacts.find(candidateSetId).orElseThrow(() ->
                new IllegalStateException("planner candidate set is not available: " + candidateSetId));
        if (!artifacts.executionId().equals(context.executionId())) {
            throw new SecurityException("candidate set belongs to a different graph execution");
        }
        PlanningRequest request = artifacts.request();
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
