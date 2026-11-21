package dev.supplychain.agent.runtime.cognitive.decision;

import dev.supplychain.agent.memory.retrieval.MemoryRetrievalService;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.mechanisms.b4.ObjectiveFingerprint;
import dev.supplychain.agent.mechanisms.b4.RecoveryMethodReference;
import dev.supplychain.agent.memory.common.MemoryVersionConflictException;
import dev.supplychain.agent.memory.working.MemoryValue;
import dev.supplychain.agent.memory.working.WorkingMemoryId;
import dev.supplychain.agent.memory.working.WorkingMemoryService;
import dev.supplychain.agent.memory.working.WorkingMemorySnapshot;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateFactory;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidate;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateReference;
import dev.supplychain.agent.mechanisms.b1.B1MemoryAttribution;
import dev.supplychain.agent.mechanisms.b1.B1MemoryFeatureLearningService;
import dev.supplychain.agent.mechanisms.b1.B1RetrievalSelector;
import dev.supplychain.agent.mechanisms.b1.B1SelectionResult;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedurePromotionService;
import dev.supplychain.agent.mechanisms.b2.ReusableRecoveryProcedure;
import dev.supplychain.agent.mechanisms.b3.WorkspaceAdmissionService;
import dev.supplychain.agent.mechanisms.b3.WorkspaceAdmissionRequest;
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
import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.api.PlannerSelectionOverride;
import dev.supplychain.agent.planning.api.WorldTransition;
import dev.supplychain.agent.planning.routing.PlannerRouter;
import dev.supplychain.agent.planning.routing.PlannerMethodResolver;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;
import dev.supplychain.agent.runtime.cognitive.ResourceBudgetExecutionGuard;
import dev.supplychain.agent.mechanisms.b6.ResourceBudgetExceededException;
import dev.supplychain.agent.mechanisms.b6.ResourceUnit;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment;
import dev.supplychain.agent.mechanisms.b6.UncertaintyGovernanceDirective;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final PlannerMethodResolver plannerMethodResolver;
    private final RecoveryProcedurePromotionService recoveryProcedurePromotion;
    private final B1MemoryFeatureLearningService b1FeatureLearning;

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
        this(memoryRetrieval, b1CandidateFactory, b1Selector, workspaceAdmission, workspaceBroadcast,
                plannerRouter, planningWorldModel, candidateEvaluator, planningArtifacts, domainPort,
                clock, budgetGuard, workingMemory, PlannerMethodResolver.canonical());
    }

    /** Full host composition with an explicit mapping from B4 method IDs to registered M5 planners. */
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
            WorkingMemoryService workingMemory,
            PlannerMethodResolver plannerMethodResolver) {
        this(memoryRetrieval, b1CandidateFactory, b1Selector, workspaceAdmission, workspaceBroadcast,
                plannerRouter, planningWorldModel, candidateEvaluator, planningArtifacts, domainPort,
                clock, budgetGuard, workingMemory, plannerMethodResolver, null);
    }

    /** Full composition with an explicit governed B2-to-CoALA procedural-memory reuse bridge. */
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
            WorkingMemoryService workingMemory,
            PlannerMethodResolver plannerMethodResolver,
            RecoveryProcedurePromotionService recoveryProcedurePromotion) {
        this(memoryRetrieval,b1CandidateFactory,b1Selector,workspaceAdmission,workspaceBroadcast,plannerRouter,
                planningWorldModel,candidateEvaluator,planningArtifacts,domainPort,clock,budgetGuard,workingMemory,
                plannerMethodResolver,recoveryProcedurePromotion,null);
    }

    /** Full composition with durable B1 feature learning and governed B2 procedural reuse. */
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
            WorkingMemoryService workingMemory,
            PlannerMethodResolver plannerMethodResolver,
            RecoveryProcedurePromotionService recoveryProcedurePromotion,
            B1MemoryFeatureLearningService b1FeatureLearning) {
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
        this.plannerMethodResolver = Objects.requireNonNull(plannerMethodResolver, "plannerMethodResolver");
        this.recoveryProcedurePromotion = recoveryProcedurePromotion;
        this.b1FeatureLearning = b1FeatureLearning;
    }

    public ResourceBudgetExecutionGuard resourceBudgetGuard() { return budgetGuard; }

    public EvidenceRetrieved retrieveEvidence(CognitiveCycleOperations.CycleContext context) {
        Objects.requireNonNull(context, "context");
        Instant at = clock.instant();
        UncertaintyAssessment activeUncertainty = requireCurrentUncertainty(context, at);
        var scope = domainPort.memoryScope(context);
        var query = domainPort.retrievalQuery(context, at);
        var selectionRequest = domainPort.b1SelectionRequest(context, at)
                .withUncertaintyDirective(dev.supplychain.agent.mechanisms.b6.UncertaintyGovernanceDirective.from(activeUncertainty));
        if (!scope.equals(query.scopeId()) || !scope.equals(selectionRequest.scopeId())) {
            throw new SecurityException("memory retrieval and B1 scopes must match the active cognitive-cycle scope");
        }
        var retrieved = memoryRetrieval.retrieve(query);
        List<B1MemoryCandidate> candidates = b1CandidateFactory.from(
                scope, retrieved, query.knownAt(), domainPort.b1FeatureProvider(context));
        if (b1FeatureLearning != null) candidates = b1FeatureLearning.enrich(candidates, at);
        B1SelectionResult selected = b1Selector.select(selectionRequest, candidates);
        if (!selected.diversitySatisfied()) {
            return new EvidenceRetrieved(EvidenceDisposition.FAILED, "", 1.0,
                    "B6 calibrated evidence-uncertainty governance required " + selected.requiredDistinctMemoryKinds()
                            + " distinct CoALA memory kinds but only " + selected.achievedDistinctMemoryKinds()
                            + " passed B1 accessibility, utility and context-budget constraints",
                    context.workingMemoryId(), context.workingMemoryVersion());
        }
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
        if (b1FeatureLearning != null) {
            b1FeatureLearning.recordSelected(selected.selectedCandidates(), selectionRequest.queryCues(),
                    context.executionId(), persisted.evidenceSetId(), at);
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
        var request = Objects.requireNonNull(domainPort.workspaceAdmissionRequest(context, at), "domain port returned null workspace admission request");
        request = request.withUncertaintyDirective(dev.supplychain.agent.mechanisms.b6.UncertaintyGovernanceDirective.from(activeUncertainty));
        request = attachB1MemoryAttributions(context, request, at);
        request = request.withExperimentUnitFingerprint(Objects.requireNonNull(
                        domainPort.experimentUnitFingerprint(context, at), "domain port returned null experiment-unit fingerprint"));
        if (!request.experimentUnitFingerprint().isEmpty()
                && !request.experimentUnitFingerprint().matches("[0-9a-f]{64}")) {
            throw new SecurityException("experimental unit identity must be a canonical SHA-256 scenario-input fingerprint");
        }
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
        if (outcome.status() == WorkspaceAdmissionStatus.INCOMPLETE_COVERAGE
                || outcome.status() == WorkspaceAdmissionStatus.INCOMPLETE_EVIDENCE_DIVERSITY) {
            return new WorkspacePrepared(WorkspaceDisposition.INCOMPLETE, snapshot.id(), outcome.explanation());
        }
        return new WorkspacePrepared(WorkspaceDisposition.ADMITTED, snapshot.id(),
                outcome.explanation() + "; all configured consumers acknowledged the workspace");
    }

    private WorkspaceAdmissionRequest attachB1MemoryAttributions(
            CognitiveCycleOperations.CycleContext context, WorkspaceAdmissionRequest request, Instant at) {
        var mapping = Objects.requireNonNull(domainPort.workspaceCandidateMemoryAttributions(context, at),
                "decision domain returned null B1 workspace attribution map");
        if (mapping.isEmpty()) return request;
        if (b1FeatureLearning == null) throw new IllegalStateException("B1 workspace attribution requires the B1 feature learning service");
        var selected = Objects.requireNonNull(domainPort.selectedB1MemoryCandidates(context, at),
                "decision domain returned null persisted B1 candidate selection");
        var selectedByReference = new java.util.HashMap<B1MemoryCandidateReference,B1MemoryCandidate>();
        for (B1MemoryCandidate candidate : selected) {
            if (!request.scopeId().equals(candidate.scopeId())) throw new SecurityException("persisted B1 candidate is outside workspace scope");
            String memoryReference = candidate.memoryReference();
            int colon = memoryReference.indexOf(':'); int atIndex = memoryReference.lastIndexOf('@');
            if (colon < 1 || atIndex <= colon + 1) throw new SecurityException("persisted B1 candidate has invalid memory reference");
            var reference = new B1MemoryCandidateReference(candidate.kind(), candidate.scopeId(),
                    memoryReference.substring(colon + 1, atIndex), Long.parseLong(memoryReference.substring(atIndex + 1)));
            if (selectedByReference.putIfAbsent(reference, candidate) != null) throw new SecurityException("duplicate selected B1 memory reference");
        }
        var workspaceIds = request.candidates().stream().map(dev.supplychain.agent.mechanisms.b3.WorkspaceCandidate::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!workspaceIds.containsAll(mapping.keySet())) throw new SecurityException("B1 attribution references a candidate outside the workspace request");
        var queryCues = domainPort.b1SelectionRequest(context, at).queryCues();
        var decorated = request.candidates().stream().map(candidate -> {
            var references = mapping.getOrDefault(candidate.id(), List.of());
            if (references.isEmpty()) return candidate;
            var fingerprint = dev.supplychain.agent.mechanisms.b3.B3OutcomeEvidenceService.candidateFingerprint(candidate);
            var attributions = references.stream().map(reference -> {
                B1MemoryCandidate source = selectedByReference.get(reference);
                if (source == null) throw new SecurityException("workspace item attributes a memory revision not selected in its persisted B1 evidence set");
                return b1FeatureLearning.attribute(source, queryCues, fingerprint);
            }).toList();
            return candidate.withMemoryAttributions(attributions);
        }).toList();
        return request.withCandidates(decorated);
    }

    public PlanningPrepared proposeCandidates(CognitiveCycleOperations.CycleContext context) {
        Objects.requireNonNull(context, "context");
        Instant at = clock.instant();
        UncertaintyAssessment activeUncertainty = requireCurrentUncertainty(context, at);
        PlanningRequest request = Objects.requireNonNull(
                domainPort.planningRequest(context, at), "domain port returned null planning request")
                .withUncertaintyDirective(dev.supplychain.agent.mechanisms.b6.UncertaintyGovernanceDirective.from(activeUncertainty));
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

        // B2's approved-and-activated procedures are retrieved by exact trusted condition keys
        // on every planning cycle. The service revalidates scope, evidence and action descriptors.
        Set<String> recoveryConditionKeys = Set.copyOf(Objects.requireNonNull(
                domainPort.recoveryProcedureConditionKeys(context, at), "decision domain returned null B2 condition keys"));
        if (recoveryConditionKeys.stream().anyMatch(key -> key == null || !key.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,159}"))) {
            throw new SecurityException("B2 procedure retrieval requires registered opaque condition keys");
        }
        if (!recoveryConditionKeys.isEmpty()) {
            if (recoveryProcedurePromotion == null) {
                throw new IllegalStateException("B2 recovery condition keys were supplied but governed procedural retrieval is not configured");
            }
            var recoveryScope = request.workingMemory().scopeId();
            List<ReusableRecoveryProcedure> reusable = recoveryConditionKeys.stream().sorted()
                    .map(key -> recoveryProcedurePromotion.retrieveActive(recoveryScope, key, at))
                    .flatMap(java.util.Optional::stream).toList();
            request = request.withReusableRecoveryProcedures(reusable);
        }

        // B6 hypothesis scrutiny is a host-owned, independent review performed after all
        // planning inputs (including B2 procedures and B4 method authority) are fixed, but before
        // M5 receives the request. A proof from an earlier/different request is not reusable.
        if (request.alternativeHypothesisReview() != null) {
            throw new SecurityException("planning host cannot pre-populate B6 alternative-hypothesis review evidence");
        }

        // B4 method authority is resolved from the current persisted intention, not from an
        // LLM proposal or a candidate-plan reference. Unknown methods fail closed.
        PlannerSelectionOverride methodOverride = null;
        if (!context.intentionId().isBlank()) {
            if (context.planningMethodId().isBlank() || context.planningCandidatePlanReference().isBlank()) {
                throw new SecurityException("active B4 intention is missing its authoritative planning-method reference");
            }
            String requestObjectiveFingerprint = ObjectiveFingerprint.from(request.objective()).value();
            if (context.intentionObjectiveFingerprint().isBlank()
                    || !context.intentionObjectiveFingerprint().equals(requestObjectiveFingerprint)) {
                throw new SecurityException("persisted B4 intention objective fingerprint does not match the planning request objective");
            }
            RecoveryMethodReference method = new RecoveryMethodReference(context.planningMethodId(),
                    context.planningCandidatePlanReference());
            PlannerId plannerId = Objects.requireNonNull(plannerMethodResolver.resolve(method),
                    "B4 planner-method resolver returned null");
            methodOverride = new PlannerSelectionOverride(plannerId, method.methodId(),
                    method.candidatePlanReference(), context.intentionId(), context.intentionVersion(),
                    context.intentionObjectiveFingerprint(),
                    "Resolved from the current versioned B4 intention; the committed objective is unchanged");
        } else if (!context.planningMethodId().isBlank()) {
            throw new SecurityException("planning method reference cannot be used without its authoritative B4 intention");
        }

        // Rebuild the request after B4 has supplied its authoritative planner binding so that
        // the independent B6 reviewer sees exactly the method that M5 will use.
        var originalBudget = request.budget();
        var effectiveBudget = originalBudget.scaleComputeResources(context.plannerBudgetScalePercent());
        request = request.withPlannerControl(effectiveBudget, methodOverride);
        if (request.uncertaintyDirective().requireAlternativeHypothesisReview()) {
            var review = domainPort.independentlyReviewAlternativeHypotheses(context, request, at)
                    .orElseThrow(() -> new SecurityException(
                            "B6 calibrated hypothesis uncertainty requires an independent alternative-hypothesis review before M5"));
            if (review.reviewedAt().isBefore(request.requestedAt()) || review.reviewedAt().isAfter(at)) {
                throw new SecurityException("B6 alternative-hypothesis review timestamp is outside the current planning/review interval");
            }
            request = request.withAlternativeHypothesisReview(review);
        }
        request.assertGovernanceComplete();
        final PlanningRequest controlledRequest = request;
        PlanningResult result;
        if (context.plannerBudgetScalePercent() < 100 && effectiveBudget.equals(originalBudget)) {
            var selection = plannerRouter.select(controlledRequest);
            result = new PlanningResult(selection.plannerId(), List.of(), PlannerTerminationReason.RESOURCE_BUDGET_EXHAUSTED,
                    new dev.supplychain.agent.planning.api.PlanningStatistics(0, 0, 0, 0, 0, 0, Duration.ZERO),
                    "b6-downshift-exhausted-" + controlledRequest.fingerprint().substring(0, 12),
                    "B6 requested a smaller planner compute envelope, but every consumable limit is already at its minimum; planner invocation was refused",
                    List.of());
        } else if (budgetGuard == null) {
            result = plannerRouter.plan(controlledRequest);
        } else {
            var plannerBudget = controlledRequest.budget();
            ResourceUsage reserved = new ResourceUsage(Map.of(
                    ResourceUnit.SEARCH_EXPANSIONS, (long) plannerBudget.maxExpansions(),
                    ResourceUnit.REASONING_EVALUATIONS, (long) plannerBudget.maxEvaluations(),
                    ResourceUnit.MODEL_CALLS, (long) plannerBudget.maxModelCalls(),
                    ResourceUnit.MCTS_ROLLOUTS, (long) plannerBudget.maxRollouts()));
            try {
                // The planning-input fingerprint intentionally excludes consumable resource ceilings so a
                // planner session can resume under a replenished or reduced budget. The reservation identity
                // must therefore include the graph planning round and the applied per-call budget envelope:
                // exact checkpoint retries remain idempotent, while a B6 downshift receives a fresh lease.
                String plannerOperationId = plannerOperationId(context, controlledRequest);
                result = budgetGuard.execute(context.executionId(), plannerOperationId, reserved,
                        () -> plannerRouter.plan(controlledRequest), planned -> new ResourceUsage(Map.of(
                                ResourceUnit.SEARCH_EXPANSIONS, planned.statistics().expansions(),
                                ResourceUnit.REASONING_EVALUATIONS, planned.statistics().evaluations(),
                                ResourceUnit.MODEL_CALLS, planned.statistics().modelCalls(),
                                ResourceUnit.MCTS_ROLLOUTS, planned.statistics().rollouts())));
            } catch (ResourceBudgetExceededException exhausted) {
                var selection = plannerRouter.select(controlledRequest);
                result = new PlanningResult(selection.plannerId(), List.of(), PlannerTerminationReason.RESOURCE_BUDGET_EXHAUSTED,
                        new dev.supplychain.agent.planning.api.PlanningStatistics(0, 0, 0, 0, 0, 0, Duration.ZERO),
                        "budget-blocked-" + controlledRequest.fingerprint().substring(0, 12),
                        selection.explanation() + "; durable B6 reservation refused: " + exhausted.getMessage(), List.of());
            }
        }
        String candidateSetId = planningArtifacts.store(controlledRequest, result, clock.instant());
        return new PlanningPrepared(result.plannerId(), result.sessionId(), candidateSetId,
                result.candidates().size(), result.terminationReason());
    }

    private static String plannerOperationId(CognitiveCycleOperations.CycleContext context,
                                             PlanningRequest request) {
        return plannerOperationId(context.planningRound(), context.plannerBudgetScalePercent(),
                request.budget(), request.fingerprint());
    }

    static String plannerOperationId(int planningRound, int budgetScalePercent,
                                     dev.supplychain.agent.planning.api.PlannerBudget budget,
                                     String requestFingerprint) {
        if (planningRound < 0) throw new IllegalArgumentException("planningRound cannot be negative");
        if (budgetScalePercent < 1 || budgetScalePercent > 100) {
            throw new IllegalArgumentException("budgetScalePercent must be between 1 and 100");
        }
        Objects.requireNonNull(budget, "budget");
        String fingerprint = Objects.requireNonNull(requestFingerprint, "requestFingerprint").trim();
        if (!fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("requestFingerprint must be lowercase SHA-256 hex");
        }
        // The planning fingerprint intentionally excludes consumable limits for session resume.
        // Include the round, scale and complete effective budget here to distinguish a new planner
        // invocation from a checkpoint retry and from a B6-downshifted invocation.
        return "m5-planner:round=" + planningRound
                + ":scale=" + budgetScalePercent
                + ":budget=" + budget
                + ":request=" + fingerprint;
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
        if (freshRequest.alternativeHypothesisReview() == null
                && freshRequest.uncertaintyDirective().requireAlternativeHypothesisReview()) {
            boolean reusedExactReview = false;
            if (original.alternativeHypothesisReview() != null) {
                try {
                    // Reuse only if the fresh authoritative inputs have the same pre-review
                    // fingerprint. Any material change requires a new independent review.
                    freshRequest = freshRequest.withAlternativeHypothesisReview(original.alternativeHypothesisReview());
                    reusedExactReview = true;
                } catch (SecurityException staleReview) {
                    // The host will be asked to review the changed request below.
                }
            }
            if (!reusedExactReview) {
                var freshReview = domainPort.independentlyReviewAlternativeHypotheses(context, freshRequest, clock.instant())
                        .orElse(null);
                if (freshReview == null) return new CandidateRevalidation(false,
                        "B6 calibrated hypothesis uncertainty requires a fresh independent review for changed planning inputs");
                try {
                    freshRequest = freshRequest.withAlternativeHypothesisReview(freshReview);
                } catch (SecurityException invalidReview) {
                    return new CandidateRevalidation(false, "fresh B6 alternative-hypothesis review is invalid: "
                            + invalidReview.getMessage());
                }
            }
        }
        try {
            freshRequest.assertGovernanceComplete();
        } catch (SecurityException incompleteGovernance) {
            return new CandidateRevalidation(false, incompleteGovernance.getMessage());
        }
        if (!Objects.equals(freshRequest.alternativeHypothesisReview(), original.alternativeHypothesisReview())
                && freshRequest.alternativeHypothesisReview() == null) {
            return new CandidateRevalidation(false,
                    "fresh planning request does not carry required independent B6 alternative-hypothesis review evidence");
        }
        if (!Objects.equals(freshRequest.alternativeHypothesisReview(), original.alternativeHypothesisReview())) {
            return new CandidateRevalidation(false,
                    "fresh planning request does not preserve the exact independent B6 alternative-hypothesis review");
        }
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
        if (!replay.feasible()) return new CandidateRevalidation(false,
                "selected candidate no longer satisfies deterministic transitions and hard constraints");
        if (freshRequest.uncertaintyDirective().requireIndependentPlanVerification()) {
            IndependentPlanVerification proof = domainPort.independentlyVerifySelectedPlan(context, selected,
                    freshRequest, clock.instant()).orElse(null);
            if (proof == null) return new CandidateRevalidation(false,
                    "B6 calibrated plan uncertainty requires a separate host verifier; no proof was supplied");
            if (!proof.executionId().equals(context.executionId())
                    || !proof.candidateId().equals(selected.candidate().candidateId())
                    || !proof.objectiveFingerprint().equals(ObjectiveFingerprint.from(freshRequest.objective()).value())
                    || !proof.planningRequestFingerprint().equals(freshRequest.fingerprint())
                    || !proof.candidateFingerprint().equals(IndependentPlanVerification.fingerprint(selected.candidate()))
                    || proof.verifiedAt().isBefore(freshRequest.requestedAt())
                    || proof.verifiedAt().isAfter(clock.instant())
                    || proof.verifierId().isBlank() || proof.evidenceReference().isBlank()) {
                return new CandidateRevalidation(false, "independent plan verification is not bound to the exact selected candidate and fresh request");
            }
        }
        return new CandidateRevalidation(true, "selected candidate passed deterministic replay and all required B6 independent verification");
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
