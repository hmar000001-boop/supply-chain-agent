package dev.supplychain.agent.runtime.cognitive.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.EntityReference;
import dev.supplychain.agent.memory.common.Provenance;
import dev.supplychain.agent.memory.common.ProvenanceSource;
import dev.supplychain.agent.memory.working.WorkingMemorySnapshot;
import dev.supplychain.agent.memory.working.WorkingMemoryService;
import dev.supplychain.agent.memory.working.WorkingMemoryId;
import dev.supplychain.agent.memory.semantic.FactKey;
import dev.supplychain.agent.memory.semantic.FactValue;
import dev.supplychain.agent.memory.semantic.SemanticFact;
import dev.supplychain.agent.domain.catalog.Product;
import dev.supplychain.agent.domain.catalog.ProductId;
import dev.supplychain.agent.domain.common.Quantity;
import dev.supplychain.agent.domain.common.Money;
import dev.supplychain.agent.domain.common.UnitOfMeasure;
import dev.supplychain.agent.domain.orders.CustomerOrder;
import dev.supplychain.agent.domain.orders.CustomerOrderId;
import dev.supplychain.agent.domain.orders.OrderLine;
import dev.supplychain.agent.domain.orders.OrderLineId;
import dev.supplychain.agent.domain.orders.OrderPriority;
import dev.supplychain.agent.domain.orders.OrderStatus;
import dev.supplychain.agent.domain.inventory.InventoryLocation;
import dev.supplychain.agent.domain.inventory.InventoryLocationId;
import dev.supplychain.agent.domain.inventory.InventoryLot;
import dev.supplychain.agent.domain.inventory.InventoryLotId;
import dev.supplychain.agent.domain.inventory.LotQualityStatus;
import dev.supplychain.agent.domain.inventory.ReservationId;
import dev.supplychain.agent.domain.recovery.RecoveryAction;
import dev.supplychain.agent.domain.recovery.RecoveryConstraint;
import dev.supplychain.agent.domain.recovery.RecoveryObjective;
import dev.supplychain.agent.domain.recovery.RecoveryObjectiveId;
import dev.supplychain.agent.domain.recovery.RecoveryObjectiveStatus;
import dev.supplychain.agent.domain.world.SupplyChainSnapshot;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.inmemory.InMemoryEpisodeRepository;
import dev.supplychain.agent.memory.inmemory.InMemoryProceduralMemoryRepository;
import dev.supplychain.agent.memory.inmemory.InMemorySemanticFactRepository;
import dev.supplychain.agent.memory.inmemory.InMemoryWorkingMemoryRepository;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalQuery;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalService;
import dev.supplychain.agent.mechanisms.b1.B1CandidateFeatureProvider;
import dev.supplychain.agent.mechanisms.b1.B1CandidateFeatures;
import dev.supplychain.agent.mechanisms.b1.MaintenanceProfile;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateFactory;
import dev.supplychain.agent.mechanisms.b1.B1RetrievalSelector;
import dev.supplychain.agent.mechanisms.b1.B1ScoringPolicy;
import dev.supplychain.agent.mechanisms.b1.B1SelectionRequest;
import dev.supplychain.agent.mechanisms.b1.ExponentialActivationLatencyModel;
import dev.supplychain.agent.mechanisms.b1.RecencyFrequencyActivationEstimator;
import dev.supplychain.agent.mechanisms.b3.ConsumerReceiptStatus;
import dev.supplychain.agent.mechanisms.b3.InMemoryWorkspaceRepository;
import dev.supplychain.agent.mechanisms.b3.WorkspaceAdmissionRequest;
import dev.supplychain.agent.mechanisms.b3.WorkspaceAdmissionService;
import dev.supplychain.agent.mechanisms.b3.WorkspaceBroadcastReport;
import dev.supplychain.agent.mechanisms.b3.WorkspaceBroadcastService;
import dev.supplychain.agent.mechanisms.b3.WorkspaceCandidate;
import dev.supplychain.agent.mechanisms.b3.WorkspaceCandidateKind;
import dev.supplychain.agent.mechanisms.b3.WorkspaceConsumerReceipt;
import dev.supplychain.agent.mechanisms.b3.WorkspaceScoringPolicy;
import dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment;
import dev.supplychain.agent.mechanisms.b6.UncertaintyDimension;
import dev.supplychain.agent.mechanisms.b6.UncertaintySignal;
import dev.supplychain.agent.actions.api.ActionCategory;
import dev.supplychain.agent.actions.api.ActionDescriptor;
import dev.supplychain.agent.actions.api.ActionEffect;
import dev.supplychain.agent.actions.api.Capability;
import dev.supplychain.agent.planning.api.ActionImpactEstimate;
import dev.supplychain.agent.planning.api.CandidateEvaluator;
import dev.supplychain.agent.planning.api.DeterministicCandidateEvaluator;
import dev.supplychain.agent.planning.api.PlannerBudget;
import dev.supplychain.agent.planning.api.PlanningAction;
import dev.supplychain.agent.planning.api.PlanCandidate;
import dev.supplychain.agent.planning.api.PlanningActionProvider;
import dev.supplychain.agent.planning.api.PlanningAuthorityPolicy;
import dev.supplychain.agent.planning.api.PlanningComplexity;
import dev.supplychain.agent.planning.api.PlanningRiskPolicy;
import dev.supplychain.agent.planning.api.PlanningScoringProfile;
import dev.supplychain.agent.planning.api.SimulatorBackedPlanningWorldModel;
import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.api.PlannerTerminationReason;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningResult;
import dev.supplychain.agent.planning.api.PlanningStatistics;
import dev.supplychain.agent.planning.api.PlanningStrategy;
import dev.supplychain.agent.planning.api.PlanningState;
import dev.supplychain.agent.planning.api.PlanningWorldModel;
import dev.supplychain.agent.planning.api.PlanEvaluation;
import dev.supplychain.agent.planning.routing.DefaultPlannerSelectionPolicy;
import dev.supplychain.agent.planning.routing.PlannerRouter;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;
import dev.supplychain.agent.runtime.cognitive.ResourceBudgetExecutionGuard;
import dev.supplychain.agent.mechanisms.b6.InMemoryResourceBudgetLedger;
import dev.supplychain.agent.mechanisms.b6.ResourceBudget;
import dev.supplychain.agent.mechanisms.b6.ResourceUnit;
import dev.supplychain.agent.simulator.SupplyChainSimulator;
import dev.supplychain.agent.simulator.command.SimulationCommand;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CognitiveCycleDecisionServicesTest {
    private static final Instant NOW = Instant.parse("2026-11-17T20:00:00Z");
    private static final MemoryScopeId SCOPE = new MemoryScopeId("tenant:test");

    @Test
    void plannerReservationIdentityIsStableForCheckpointRetryAndUniqueForNewRoundOrBudgetEnvelope() {
        var full = new PlannerBudget(4, 100, 80, 40, 20, 8,
                Duration.ofSeconds(10), NOW.plusSeconds(60));
        var reduced = full.scaleComputeResources(50);
        String fingerprint = "a".repeat(64);

        String firstAttempt = CognitiveCycleDecisionServices.plannerOperationId(0, 100, full, fingerprint);
        assertEquals(firstAttempt,
                CognitiveCycleDecisionServices.plannerOperationId(0, 100, full, fingerprint),
                "a checkpoint retry of the same planning attempt must retain the same idempotency key");
        assertNotEquals(firstAttempt,
                CognitiveCycleDecisionServices.plannerOperationId(1, 50, reduced, fingerprint),
                "a B6-downshifted next planning round must obtain a fresh resource reservation");
        assertNotEquals(firstAttempt,
                CognitiveCycleDecisionServices.plannerOperationId(0, 100, reduced, fingerprint),
                "a changed effective resource envelope must not reuse the prior reservation");
        assertNotEquals(firstAttempt,
                CognitiveCycleDecisionServices.plannerOperationId(1, 100, full, fingerprint),
                "a distinct planner round must not reuse the prior committed reservation");
    }

    @Test
    void retrievalUsesM2BeforeB1AndPersistsTheSelectedEvidenceSet() {
        var port = new TestDecisionPort();
        var services = services(port, workspaceBroadcast());

        var result = services.retrieveEvidence(context());

        assertEquals(CognitiveCycleOperations.EvidenceDisposition.READY, result.disposition());
        assertEquals("evidence-set:test", result.evidenceSetId());
        assertNotNull(port.retrieved);
        assertNotNull(port.selected);
        assertTrue(port.retrieved.verifiedEpisodes().isEmpty());
        assertTrue(port.selected.selectedCandidates().isEmpty());
    }

    @Test
    void selectedLongTermMemoryIsProjectedAsVersionedEvidenceReferencesIntoWorkingMemory() {
        var port = new TestDecisionPort();
        var workingRepository = new InMemoryWorkingMemoryRepository();
        var workingService = new WorkingMemoryService(workingRepository, Duration.ofDays(1));
        var initialWorkingMemory = workingService.open(SCOPE, NOW);
        var semanticRepository = new InMemorySemanticFactRepository();
        var subject = new EntityReference("supplier", "supplier:acme");
        Instant recordedAt = NOW.minusSeconds(60);
        var proposed = semanticRepository.stage(SemanticFact.proposed(
                new FactKey(SCOPE, subject, "lead-time-days"), new FactValue.Decimal(java.math.BigDecimal.valueOf(7)),
                recordedAt, null, recordedAt,
                new Provenance(ProvenanceSource.AUTHORITATIVE_SYSTEM, "erp:supplier:acme", recordedAt, recordedAt, "erp-v1")));
        semanticRepository.activate(proposed.id(), proposed.version(), "reviewer:1",
                new EvidenceReference("approval", "semantic-fact-approved"), NOW.minusSeconds(30));

        port.retrievalEntity = subject;
        port.retrievalPredicate = "lead-time-days";
        port.featureProvider = ignored -> new B1CandidateFeatures(List.of(), Map.of(), 10.0, 1,
                Duration.ofMillis(1), new MaintenanceProfile(0.0, 0.0, 0.0, Duration.ofDays(1), NOW));
        var retrieval = new MemoryRetrievalService(workingRepository, new InMemoryEpisodeRepository(),
                semanticRepository, new InMemoryProceduralMemoryRepository());
        var services = composedServices(port, workspaceBroadcast(), new EmptyWorldModel(), unusedEvaluator(),
                null, retrieval, workingService);

        var result = services.retrieveEvidence(context(initialWorkingMemory, ""));

        assertEquals(CognitiveCycleOperations.EvidenceDisposition.READY, result.disposition());
        assertEquals(initialWorkingMemory.id().value(), result.workingMemoryId());
        assertEquals(1L, result.workingMemoryVersion());
        var updated = workingService.load(initialWorkingMemory.id());
        var projected = updated.values().get(CognitiveCycleDecisionServices.COALA_RETRIEVED_MEMORY_REFERENCES_KEY);
        assertNotNull(projected);
        assertEquals(new dev.supplychain.agent.memory.working.MemoryValue.TextList(List.of(
                "@projection:v1|execution=execution:test|basedOnWorkingMemoryVersion=0",
                "semantic:" + proposed.id().value() + "@1")), projected.value());
        assertEquals(List.of(new EvidenceReference("coala-selected-memory",
                "semantic:" + proposed.id().value() + "@1")), projected.evidence());
        assertEquals(projected.evidence(), updated.sourceEvidence());
    }

    @Test
    void selectedLongTermMemoryProjectionIsIdempotentAfterGraphCheckpointInterruption() {
        var port = new TestDecisionPort();
        var workingRepository = new InMemoryWorkingMemoryRepository();
        var workingService = new WorkingMemoryService(workingRepository, Duration.ofDays(1));
        var initialWorkingMemory = workingService.open(SCOPE, NOW);
        var semanticRepository = new InMemorySemanticFactRepository();
        seedSemanticFact(semanticRepository);
        port.retrievalEntity = new EntityReference("supplier", "supplier:acme");
        port.retrievalPredicate = "lead-time-days";
        port.featureProvider = ignored -> new B1CandidateFeatures(List.of(), Map.of(), 10.0, 1,
                Duration.ofMillis(1), new MaintenanceProfile(0.0, 0.0, 0.0, Duration.ofDays(1), NOW));
        var retrieval = new MemoryRetrievalService(workingRepository, new InMemoryEpisodeRepository(),
                semanticRepository, new InMemoryProceduralMemoryRepository());
        var services = composedServices(port, workspaceBroadcast(), new EmptyWorldModel(), unusedEvaluator(),
                null, retrieval, workingService);
        var originalContext = context(initialWorkingMemory, "");

        var first = services.retrieveEvidence(originalContext);
        var replay = services.retrieveEvidence(originalContext);

        assertEquals(CognitiveCycleOperations.EvidenceDisposition.READY, first.disposition());
        assertEquals(CognitiveCycleOperations.EvidenceDisposition.READY, replay.disposition());
        assertEquals(1L, first.workingMemoryVersion());
        assertEquals(1L, replay.workingMemoryVersion());
        assertEquals(1L, workingService.load(initialWorkingMemory.id()).version(),
                "exact replay must not increment working-memory version again");
    }

    @Test
    void selectedLongTermMemoryCannotBeProjectedAcrossWorkingMemoryVersionConflict() {
        var port = new TestDecisionPort();
        var workingRepository = new InMemoryWorkingMemoryRepository();
        var workingService = new WorkingMemoryService(workingRepository, Duration.ofDays(1));
        var initialWorkingMemory = workingService.open(SCOPE, NOW);
        var semanticRepository = new InMemorySemanticFactRepository();
        seedSemanticFact(semanticRepository);
        port.retrievalEntity = new EntityReference("supplier", "supplier:acme");
        port.retrievalPredicate = "lead-time-days";
        port.featureProvider = ignored -> new B1CandidateFeatures(List.of(), Map.of(), 10.0, 1,
                Duration.ofMillis(1), new MaintenanceProfile(0.0, 0.0, 0.0, Duration.ofDays(1), NOW));
        // Advance working memory after the graph captured version zero.
        workingService.put(initialWorkingMemory.id(), 0L, "concurrent-update",
                new dev.supplychain.agent.memory.working.MemoryValue.Flag(true), List.of(), NOW);
        var retrieval = new MemoryRetrievalService(workingRepository, new InMemoryEpisodeRepository(),
                semanticRepository, new InMemoryProceduralMemoryRepository());
        var services = composedServices(port, workspaceBroadcast(), new EmptyWorldModel(), unusedEvaluator(),
                null, retrieval, workingService);

        var result = services.retrieveEvidence(context(initialWorkingMemory, ""));

        assertEquals(CognitiveCycleOperations.EvidenceDisposition.FAILED, result.disposition());
        assertTrue(result.reason().contains("version changed"));
        var current = workingService.load(initialWorkingMemory.id());
        assertEquals(1L, current.version());
        assertTrue(!current.values().containsKey(CognitiveCycleDecisionServices.COALA_RETRIEVED_MEMORY_REFERENCES_KEY));
        assertEquals("evidence-set:test", port.persistedEvidenceSetId,
                "the selected evidence set remains auditable even though stale WM projection failed closed");
    }

    private static void seedSemanticFact(InMemorySemanticFactRepository repository) {
        Instant recordedAt = NOW.minusSeconds(60);
        var proposed = repository.stage(SemanticFact.proposed(
                new FactKey(SCOPE, new EntityReference("supplier", "supplier:acme"), "lead-time-days"),
                new FactValue.Decimal(java.math.BigDecimal.valueOf(7)), recordedAt, null, recordedAt,
                new Provenance(ProvenanceSource.AUTHORITATIVE_SYSTEM, "erp:supplier:acme", recordedAt, recordedAt, "v1")));
        repository.activate(proposed.id(), proposed.version(), "reviewer:1",
                new EvidenceReference("approval", "approved"), NOW.minusSeconds(30));
    }

    private static CandidateEvaluator unusedEvaluator() {
        return new CandidateEvaluator() {
            @Override public String configurationFingerprint() { return "decision-test-unused-evaluator-v1"; }
            @Override public PlanEvaluation evaluate(PlanningRequest request, PlanningState state) {
                throw new UnsupportedOperationException("planning evaluation is not part of these tests");
            }
        };
    }

    @Test
    void workspaceMustBeAdmittedAndAcknowledgedByEveryConfiguredConsumer() {
        var port = new TestDecisionPort();
        var services = services(port, workspaceBroadcast());

        var result = services.prepareWorkspace(context());

        assertEquals(CognitiveCycleOperations.WorkspaceDisposition.ADMITTED, result.disposition());
        assertTrue(!result.workspaceSnapshotId().isBlank());
        assertTrue(port.broadcastReport.allAccepted());
        assertEquals(1, port.broadcastReport.receipts().size());
        assertEquals(ConsumerReceiptStatus.ACCEPTED, port.broadcastReport.receipts().getFirst().status());
    }

    @Test
    void workspaceAdmissionRejectsARequestBoundToAnotherUncertaintyRevision() {
        var port = new TestDecisionPort();
        port.workspaceAssessmentReferenceOverride = new UncertaintyAssessment.Reference(
                "assessment:execution:test", 2, "execution:test", SCOPE.value());
        var services = services(port, workspaceBroadcast());

        assertThrows(SecurityException.class, () -> services.prepareWorkspace(context()));
        assertNull(port.broadcastReport, "a stale uncertainty binding must be rejected before B3 admission or broadcast");
    }

    @Test
    void broadcastFailureCannotBeReportedAsAnAdmittedWorkspace() {
        var port = new TestDecisionPort();
        var failing = new WorkspaceBroadcastService(Map.of("planner", broadcast ->
                new WorkspaceConsumerReceipt("planner", broadcast.snapshot().id(),
                        ConsumerReceiptStatus.FAILED, List.of(), NOW, "downstream unavailable")));
        var services = services(port, failing);

        var result = services.prepareWorkspace(context());

        assertEquals(CognitiveCycleOperations.WorkspaceDisposition.FAILED, result.disposition());
        assertTrue(result.reason().contains("planner=FAILED"));
    }

    @Test
    void plannerRouterRunsM5AndCandidateArtifactsAreReopenedForEvaluation() {
        var port = new TestDecisionPort();
        var workingMemory = WorkingMemorySnapshot.empty(SCOPE, NOW, NOW.plus(Duration.ofDays(1)));
        port.planningRequest = planningRequest("execution:test", workingMemory);
        var world = new SimulatorBackedPlanningWorldModel(new SupplyChainSimulator(),
                (request, state) -> List.of(), "decision-test-world-v1");
        var evaluator = new DeterministicCandidateEvaluator(world);
        var services = services(port, workspaceBroadcast(), world, evaluator);

        var prepared = services.proposeCandidates(context(workingMemory, ""));
        var evaluated = services.evaluateCandidates(context(workingMemory, prepared.candidateSetId(),
                prepared.plannerId().name(), prepared.plannerSessionId()));

        assertEquals(PlannerId.COT_SEQUENTIAL, prepared.plannerId());
        assertEquals(List.of(PlannerId.COT_SEQUENTIAL), port.plannerInvocations);
        assertTrue(!prepared.candidateSetId().isBlank());
        assertEquals(CognitiveCycleOperations.EvaluationDisposition.NO_FEASIBLE_CANDIDATE,
                evaluated.disposition());
        assertTrue(evaluated.reason().contains("NO_AVAILABLE_ACTIONS"));
    }

    @Test
    void plannerRequestMustCarryTheCurrentUncertaintyRevision() {
        var port = new TestDecisionPort();
        var workingMemory = WorkingMemorySnapshot.empty(SCOPE, NOW, NOW.plus(Duration.ofDays(1)));
        PlanningRequest valid = planningRequest("execution:test", workingMemory);
        port.planningRequest = new PlanningRequest(valid.executionId(), valid.objective(), valid.initialWorld(),
                valid.workingMemory(), valid.evidence(), valid.availableActionDefinitions(), valid.hardConstraints(),
                valid.scoringProfile(), valid.riskPolicy(), valid.authorityPolicy(), valid.complexity(), valid.budget(),
                valid.requestedAt(), valid.resumeSessionId(), new UncertaintyAssessment.Reference(
                        "assessment:execution:test", 2, "execution:test", SCOPE.value()));
        var world = new SimulatorBackedPlanningWorldModel(new SupplyChainSimulator(),
                (request, state) -> List.of(), "decision-test-world-v1");
        var services = services(port, workspaceBroadcast(), world, new DeterministicCandidateEvaluator(world));

        assertThrows(SecurityException.class, () -> services.proposeCandidates(context(workingMemory, "")));
        assertTrue(port.plannerInvocations.isEmpty(), "planner must not run under a stale uncertainty revision");
    }

    @Test
    void durableB6BudgetRefusalPreventsM5PlannerInvocationAndProducesTypedStopResult() {
        var port = new TestDecisionPort();
        var workingMemory = WorkingMemorySnapshot.empty(SCOPE, NOW, NOW.plus(Duration.ofDays(1)));
        port.planningRequest = planningRequest("execution:test", workingMemory);
        var ledger = new InMemoryResourceBudgetLedger();
        var budget = new ResourceBudget(Map.of(ResourceUnit.SEARCH_EXPANSIONS, 0L), NOW.plusSeconds(60));
        var guard = new ResourceBudgetExecutionGuard(ledger, ignored -> budget, Clock.fixed(NOW, ZoneOffset.UTC));
        var world = new SimulatorBackedPlanningWorldModel(new SupplyChainSimulator(),
                (request, state) -> List.of(), "decision-test-world-v1");
        var evaluator = new DeterministicCandidateEvaluator(world);
        var services = services(port, workspaceBroadcast(), world, evaluator, guard);

        var prepared = services.proposeCandidates(context(workingMemory, ""));
        var evaluated = services.evaluateCandidates(context(workingMemory, prepared.candidateSetId(),
                prepared.plannerId().name(), prepared.plannerSessionId()));

        assertEquals(PlannerTerminationReason.RESOURCE_BUDGET_EXHAUSTED, prepared.terminationReason());
        assertEquals(CognitiveCycleOperations.EvaluationDisposition.NEEDS_MORE_SEARCH, evaluated.disposition());
        assertTrue(port.plannerInvocations.isEmpty(), "B6 must refuse the work before entering a planner");
        assertEquals(0L, ledger.snapshot("execution:test", NOW).consumed()
                .getOrDefault(ResourceUnit.SEARCH_EXPANSIONS, 0L));
    }

    @Test
    void m5PlannerCommitsMeasuredStatisticsToTheSharedB6Budget() {
        var port = new TestDecisionPort();
        var workingMemory = WorkingMemorySnapshot.empty(SCOPE, NOW, NOW.plus(Duration.ofDays(1)));
        port.planningRequest = planningRequest("execution:test", workingMemory);
        port.plannerResultFactory = request -> new PlanningResult(PlannerId.COT_SEQUENTIAL, List.of(),
                PlannerTerminationReason.NO_AVAILABLE_ACTIONS,
                new PlanningStatistics(1, 1, 0, 0, 0, 0, Duration.ofMillis(2)),
                "budgeted-planner-session", "bounded deterministic no-action result", List.of());
        var ledger = new InMemoryResourceBudgetLedger();
        var budget = new ResourceBudget(Map.of(
                ResourceUnit.SEARCH_EXPANSIONS, 10L,
                ResourceUnit.REASONING_EVALUATIONS, 10L,
                ResourceUnit.MODEL_CALLS, 5L,
                ResourceUnit.MCTS_ROLLOUTS, 5L), NOW.plusSeconds(60));
        var guard = new ResourceBudgetExecutionGuard(ledger, ignored -> budget, Clock.fixed(NOW, ZoneOffset.UTC));
        var world = new SimulatorBackedPlanningWorldModel(new SupplyChainSimulator(),
                (request, state) -> List.of(), "decision-test-world-v1");
        var services = services(port, workspaceBroadcast(), world, new DeterministicCandidateEvaluator(world), guard);

        var prepared = services.proposeCandidates(context(workingMemory, ""));

        assertEquals(PlannerTerminationReason.NO_AVAILABLE_ACTIONS, prepared.terminationReason());
        assertEquals(List.of(PlannerId.COT_SEQUENTIAL), port.plannerInvocations);
        var snapshot = ledger.snapshot("execution:test", NOW);
        assertEquals(1L, snapshot.consumed().get(ResourceUnit.SEARCH_EXPANSIONS));
        assertEquals(1L, snapshot.consumed().get(ResourceUnit.REASONING_EVALUATIONS));
        assertEquals(0L, snapshot.consumed().getOrDefault(ResourceUnit.MODEL_CALLS, 0L));
        assertEquals(0L, snapshot.consumed().getOrDefault(ResourceUnit.MCTS_ROLLOUTS, 0L));
        assertEquals(0L, snapshot.reserved().getOrDefault(ResourceUnit.SEARCH_EXPANSIONS, 0L));
    }

    @Test
    void deterministicEvaluatorSelectsOnlyTheSimulatorReplayedFeasibleCandidate() {
        var port = new TestDecisionPort();
        var workingMemory = WorkingMemorySnapshot.empty(SCOPE, NOW, NOW.plus(Duration.ofDays(1)));
        var request = validPlanningRequest("execution:test", workingMemory);
        port.planningRequest = request;
        var world = validActionWorldModel();
        var evaluator = new DeterministicCandidateEvaluator(world);
        port.plannerResultFactory = planningRequest -> {
            var initial = world.initialState(planningRequest);
            var proposed = world.availableActions(planningRequest, initial).getFirst();
            var transition = world.apply(planningRequest, initial, proposed);
            if (!(transition instanceof dev.supplychain.agent.planning.api.WorldTransition.Applied applied)) {
                throw new IllegalStateException("test fixture action should be simulator-feasible");
            }
            var candidateEvaluation = evaluator.evaluate(planningRequest, applied.state());
            var candidate = new PlanCandidate("candidate:m6a-valid", planningRequest.objective().id(),
                    applied.state().actionPath(), proposed.supportingEvidence(), proposed.assumptions(),
                    "reserve qualified available inventory", candidateEvaluation);
            return new PlanningResult(PlannerId.COT_SEQUENTIAL, List.of(candidate), PlannerTerminationReason.COMPLETED,
                    new PlanningStatistics(1, 1, 0, 0, 0, 1, Duration.ofMillis(5)),
                    "m6a-planner-session", "deterministic test candidate", List.of());
        };
        var services = services(port, workspaceBroadcast(), world, evaluator);

        var prepared = services.proposeCandidates(context(workingMemory, ""));
        var evaluated = services.evaluateCandidates(context(workingMemory, prepared.candidateSetId(),
                prepared.plannerId().name(), prepared.plannerSessionId()));

        assertEquals(PlannerId.COT_SEQUENTIAL, prepared.plannerId());
        assertEquals(List.of(PlannerId.COT_SEQUENTIAL), port.plannerInvocations);
        assertEquals(CognitiveCycleOperations.EvaluationDisposition.SELECTED, evaluated.disposition());
        assertEquals("candidate:m6a-valid", evaluated.selectedCandidateId());
        assertTrue(evaluated.reason().contains("evaluator=deterministic-candidate-evaluator-v1"));
    }

    private static PlanningWorldModel validActionWorldModel() {
        PlanningActionProvider actions = (request, state) -> {
            var orderId = new CustomerOrderId("ORDER-M6A-ACTION");
            var lineId = new OrderLineId("LINE-M6A-ACTION");
            var lotId = new InventoryLotId("LOT-M6A-ACTION");
            var order = state.world().customerOrder(orderId);
            var line = order.lines().get(lineId);
            var committed = state.world().reservedFulfillmentQuantity(orderId, lineId)
                    .plus(state.world().committedShipmentFulfillmentQuantity(orderId, lineId));
            var outstanding = line.requestedQuantity().minus(committed);
            if (outstanding.isZero()) return List.of();
            var available = state.world().availableInventory(lotId, request.requestedAt());
            if (available.isZero()) return List.of();
            var quantity = available.compareTo(outstanding) <= 0 ? available : outstanding;
            String actionId = "reserve-" + lotId.value() + "-v" + state.world().version();
            var recovery = new RecoveryAction.ReserveInventory(orderId, lineId, lotId, quantity);
            var command = new SimulationCommand.ReserveInventory(actionId,
                    new ReservationId("RSV-" + actionId), orderId, lineId, lotId, quantity,
                    request.requestedAt().plusSeconds(state.depth()));
            return List.of(new PlanningAction(actionId,
                    new dev.supplychain.agent.actions.api.ActionId("supply-chain.grounding.inventory.reserve"),
                    "Reserve available M6-A test stock", recovery, command,
                    new ActionImpactEstimate(1.0, Money.of("10", "CAD"), Duration.ofHours(1), 0.05, 0.90),
                    List.of(new EvidenceReference("inventory-lot", lotId.value())), List.of()));
        };
        return new SimulatorBackedPlanningWorldModel(new SupplyChainSimulator(), actions, "m6a-valid-action-world-v1");
    }

    private static PlanningRequest validPlanningRequest(String executionId, WorkingMemorySnapshot workingMemory) {
        var productId = new ProductId("SKU-M6A-ACTION");
        var locationId = new InventoryLocationId("WH-M6A-ACTION");
        var lotId = new InventoryLotId("LOT-M6A-ACTION");
        var orderId = new CustomerOrderId("ORDER-M6A-ACTION");
        var lineId = new OrderLineId("LINE-M6A-ACTION");
        var objectiveId = new RecoveryObjectiveId("OBJECTIVE-M6A-ACTION");
        var quantity = Quantity.of("10", UnitOfMeasure.EACH);
        var order = new CustomerOrder(orderId, "CUSTOMER-M6A-ACTION", OrderPriority.NORMAL, OrderStatus.OPEN,
                NOW.plus(Duration.ofDays(3)), Map.of(lineId, new OrderLine(lineId, productId, quantity,
                        Quantity.zero(UnitOfMeasure.EACH))), 0);
        var objective = new RecoveryObjective(objectiveId, "Restore M6-A inventory test order", Set.of(orderId),
                NOW.plus(Duration.ofDays(2)), Set.of(RecoveryConstraint.NO_OVER_ALLOCATION,
                        RecoveryConstraint.APPROVAL_REQUIRED_FOR_EXTERNAL_COMMITMENT),
                RecoveryObjectiveStatus.ACTIVE, 0);
        var world = SupplyChainSnapshot.builder(0, NOW)
                .product(new Product(productId, "SKU-M6A-ACTION", "M6-A action test product", UnitOfMeasure.EACH, true))
                .location(new InventoryLocation(locationId, "WH-M6A-ACTION", "Montreal", true))
                .inventoryLot(new InventoryLot(lotId, productId, locationId, quantity, LotQualityStatus.AVAILABLE,
                        NOW.plus(Duration.ofDays(30)), null, null, 0))
                .customerOrder(order).recoveryObjective(objective).build();
        var descriptor = new ActionDescriptor(
                new dev.supplychain.agent.actions.api.ActionId("supply-chain.grounding.inventory.reserve"),
                "Reserve inventory", "Reserve qualified available stock", ActionCategory.GROUNDING,
                ActionEffect.DOMAIN_WRITE, Capability.INVENTORY_RESERVE, true);
        return new PlanningRequest(executionId, objective, world, workingMemory,
                List.of(new EvidenceReference("evidence-set", "evidence-set:test")), List.of(descriptor),
                objective.hardConstraints(), PlanningScoringProfile.balancedCad("1000"),
                new PlanningRiskPolicy(0.80, true),
                new PlanningAuthorityPolicy("m6a-test-policy-v1", Set.of(Capability.INVENTORY_RESERVE),
                        Set.of(Capability.INVENTORY_RESERVE)),
                new PlanningComplexity(1, 1, 0.10, 0.30),
                new PlannerBudget(2, 4, 4, 0, 0, 4, Duration.ofSeconds(30), NOW.plusSeconds(30)), NOW, null,
                new UncertaintyAssessment.Reference("assessment:" + executionId, 1, executionId, workingMemory.scopeId().value()));
    }

    private static PlanningRequest planningRequest(String executionId, WorkingMemorySnapshot workingMemory) {
        var productId = new ProductId("SKU-M6A");
        var orderId = new CustomerOrderId("ORDER-M6A");
        var lineId = new OrderLineId("LINE-M6A");
        var objectiveId = new RecoveryObjectiveId("OBJECTIVE-M6A");
        var order = new CustomerOrder(orderId, "CUSTOMER-M6A", OrderPriority.NORMAL, OrderStatus.OPEN,
                NOW.plus(Duration.ofDays(2)),
                Map.of(lineId, new OrderLine(lineId, productId, Quantity.of("1", UnitOfMeasure.EACH),
                        Quantity.zero(UnitOfMeasure.EACH))), 0);
        var objective = new RecoveryObjective(objectiveId, "Restore M6-A test order", Set.of(orderId),
                NOW.plus(Duration.ofDays(1)), Set.of(), RecoveryObjectiveStatus.ACTIVE, 0);
        var world = SupplyChainSnapshot.builder(0, NOW).product(new Product(productId, "SKU-M6A",
                "M6-A test product", UnitOfMeasure.EACH, true)).customerOrder(order)
                .recoveryObjective(objective).build();
        return new PlanningRequest(executionId, objective, world, workingMemory, List.of(), List.of(), Set.of(),
                PlanningScoringProfile.balancedCad("1000"), new PlanningRiskPolicy(1.0, false),
                new PlanningAuthorityPolicy("m6a-test-policy-v1", Set.of(), Set.of()),
                new PlanningComplexity(1, 1, 0.1, 0.1),
                new PlannerBudget(1, 1, 1, 0, 0, 1, Duration.ofSeconds(10), NOW.plusSeconds(10)),
                NOW, null, new UncertaintyAssessment.Reference("assessment:" + executionId, 1,
                        executionId, workingMemory.scopeId().value()));
    }

    private static CognitiveCycleDecisionServices services(TestDecisionPort port,
                                                            WorkspaceBroadcastService broadcast) {
        var world = new EmptyWorldModel();
        var evaluator = new CandidateEvaluator() {
            @Override public String configurationFingerprint() { return "test-evaluator-v1"; }
            @Override public PlanEvaluation evaluate(PlanningRequest request, PlanningState state) {
                throw new UnsupportedOperationException("planning evaluation is not part of these tests");
            }
        };
        return services(port, broadcast, world, evaluator);
    }

    private static CognitiveCycleDecisionServices services(TestDecisionPort port,
                                                            WorkspaceBroadcastService broadcast,
                                                            PlanningWorldModel world,
                                                            CandidateEvaluator evaluator) {
        return services(port, broadcast, world, evaluator, null);
    }

    private static CognitiveCycleDecisionServices services(TestDecisionPort port,
                                                            WorkspaceBroadcastService broadcast,
                                                            PlanningWorldModel world,
                                                            CandidateEvaluator evaluator,
                                                            ResourceBudgetExecutionGuard budgetGuard) {
        var working = new InMemoryWorkingMemoryRepository();
        var episodic = new InMemoryEpisodeRepository();
        var semantic = new InMemorySemanticFactRepository();
        var procedural = new InMemoryProceduralMemoryRepository();
        var retrieval = new MemoryRetrievalService(working, episodic, semantic, procedural);
        var workingMemoryService = new WorkingMemoryService(working, Duration.ofDays(1));
        return composedServices(port, broadcast, world, evaluator, budgetGuard, retrieval, workingMemoryService);
    }

    private static CognitiveCycleDecisionServices composedServices(TestDecisionPort port,
                    WorkspaceBroadcastService broadcast, PlanningWorldModel world, CandidateEvaluator evaluator,
                    ResourceBudgetExecutionGuard budgetGuard, MemoryRetrievalService retrieval,
                    WorkingMemoryService workingMemoryService) {
        var selector = new B1RetrievalSelector(
                new RecencyFrequencyActivationEstimator(0.5, 1.0),
                new ExponentialActivationLatencyModel(100.0, 0.5, 1, 10_000));
        var workspaceAdmission = new WorkspaceAdmissionService(new InMemoryWorkspaceRepository());
        var strategies = List.of(strategy(PlannerId.COT_SEQUENTIAL, port),
                strategy(PlannerId.TOT_BOUNDED_SEARCH, port),
                strategy(PlannerId.RAP_INSPIRED_MCTS, port));
        var router = new PlannerRouter(strategies, new DefaultPlannerSelectionPolicy());
        return new CognitiveCycleDecisionServices(retrieval, new B1MemoryCandidateFactory(), selector,
                workspaceAdmission, broadcast, router, world, evaluator,
                new InMemoryPlanningArtifactStore(), port,
                Clock.fixed(NOW, ZoneOffset.UTC), budgetGuard, workingMemoryService);
    }

    private static WorkspaceBroadcastService workspaceBroadcast() {
        return new WorkspaceBroadcastService(Map.of("planner", broadcast ->
                new WorkspaceConsumerReceipt("planner", broadcast.snapshot().id(),
                        ConsumerReceiptStatus.ACCEPTED,
                        broadcast.snapshot().entries().stream().map(e -> e.candidate().id()).toList(),
                        NOW, "accepted")));
    }

    private static PlanningStrategy strategy(PlannerId id, TestDecisionPort port) {
        return new PlanningStrategy() {
            @Override public PlannerId id() { return id; }
            @Override public PlanningResult plan(PlanningRequest request) {
                port.plannerInvocations.add(id);
                if (port.plannerResultFactory != null) return port.plannerResultFactory.apply(request);
                return new PlanningResult(id, List.of(), PlannerTerminationReason.NO_AVAILABLE_ACTIONS,
                        new PlanningStatistics(0, 0, 0, 0, 0, 0, Duration.ZERO),
                        "test-session-" + id, "deterministic no-action test result", List.of());
            }
        };
    }

    private static CognitiveCycleOperations.CycleContext context() {
        return new CognitiveCycleOperations.CycleContext(
                "request:test", "execution:test", SCOPE.value(), "Assess supplier disruption",
                "", "", "", 0, "", 0L, "evidence-set:test", 0, "", "", "", 0L,
                "", "", "", 0, "", "", "", "", "", "", "", "", "", "", "",
                new UncertaintyAssessment.Reference("assessment:execution:test", 1, "execution:test", SCOPE.value()));
    }

    private static CognitiveCycleOperations.CycleContext context(WorkingMemorySnapshot workingMemory,
                                                                   String candidateSetId) {
        return context(workingMemory, candidateSetId, "", "");
    }

    private static CognitiveCycleOperations.CycleContext context(WorkingMemorySnapshot workingMemory,
                                                                   String candidateSetId,
                                                                   String plannerId,
                                                                   String plannerSessionId) {
        return new CognitiveCycleOperations.CycleContext(
                "request:test", "execution:test", SCOPE.value(), "Assess supplier disruption",
                "", "", "", 0, workingMemory.id().value(), workingMemory.version(), "evidence-set:test", 0,
                "", "workspace:test", "", 0L, plannerId, plannerSessionId, candidateSetId, 0,
                "", "", "", "", "", "", "", "", "", "", "",
                new UncertaintyAssessment.Reference("assessment:execution:test", 1, "execution:test", SCOPE.value()));
    }

    private static UncertaintyAssessment uncertainty(CognitiveCycleOperations.CycleContext context, Instant at) {
        var reference = context.requireUncertaintyAssessmentReference();
        return new UncertaintyAssessment(reference, at.minusSeconds(1), at.plusSeconds(300),
                "test-provenance:decision", UncertaintyAssessment.Calibration.notCalibrated(), Map.of(
                UncertaintyDimension.EVIDENCE, new UncertaintySignal(UncertaintyDimension.EVIDENCE, 0.1, "evidence source", 1),
                UncertaintyDimension.HYPOTHESIS, new UncertaintySignal(UncertaintyDimension.HYPOTHESIS, 0.1, "hypothesis source", 1),
                UncertaintyDimension.PLAN, new UncertaintySignal(UncertaintyDimension.PLAN, 0.1, "plan source", 1)));
    }

    private static final class TestDecisionPort implements CognitiveCycleDecisionPort {
        private MemoryRetrievalQuery query;
        private dev.supplychain.agent.memory.retrieval.MemoryRetrievalResult retrieved;
        private dev.supplychain.agent.mechanisms.b1.B1SelectionResult selected;
        private WorkspaceBroadcastReport broadcastReport;
        private PlanningRequest planningRequest;
        private EntityReference retrievalEntity;
        private String retrievalPredicate;
        private B1CandidateFeatureProvider featureProvider;
        private String persistedEvidenceSetId = "";
        private UncertaintyAssessment.Reference workspaceAssessmentReferenceOverride;
        private java.util.function.Function<PlanningRequest, PlanningResult> plannerResultFactory;
        private final List<PlannerId> plannerInvocations = new java.util.ArrayList<>();

        @Override public MemoryScopeId memoryScope(CognitiveCycleOperations.CycleContext context) { return SCOPE; }
        @Override public MemoryRetrievalQuery retrievalQuery(CognitiveCycleOperations.CycleContext context, Instant at) {
            query = new MemoryRetrievalQuery(SCOPE, retrievalEntity, retrievalPredicate,
                    context.workingMemoryId().isBlank() ? null : new WorkingMemoryId(context.workingMemoryId()), at, at, 20);
            return query;
        }
        @Override public B1SelectionRequest b1SelectionRequest(CognitiveCycleOperations.CycleContext context, Instant at) {
            return new B1SelectionRequest(SCOPE, Set.of("supplier", "disruption"), at,
                    100, 20, Duration.ofDays(7), new B1ScoringPolicy(0.0, 0.0, 0.0, 0.0));
        }
        @Override public B1CandidateFeatureProvider b1FeatureProvider(CognitiveCycleOperations.CycleContext context) {
            if (featureProvider != null) return featureProvider;
            return reference -> { throw new AssertionError("no candidates exist in the empty-memory test"); };
        }
        @Override public CognitiveCycleOperations.EvidenceRetrieved persistEvidenceSelection(
                CognitiveCycleOperations.CycleContext context,
                dev.supplychain.agent.memory.retrieval.MemoryRetrievalResult retrieved,
                dev.supplychain.agent.mechanisms.b1.B1SelectionResult selected, Instant at) {
            this.retrieved = retrieved;
            this.selected = selected;
            this.persistedEvidenceSetId = "evidence-set:test";
            return new CognitiveCycleOperations.EvidenceRetrieved(
                    CognitiveCycleOperations.EvidenceDisposition.READY, "evidence-set:test", 0.1,
                    "empty but valid evidence set persisted");
        }
        @Override public UncertaintyAssessment currentUncertaintyAssessment(CognitiveCycleOperations.CycleContext context, Instant at) {
            return uncertainty(context, at);
        }
        @Override public WorkspaceAdmissionRequest workspaceAdmissionRequest(
                CognitiveCycleOperations.CycleContext context, Instant at) {
            return new WorkspaceAdmissionRequest("cycle:test", SCOPE, 10, 5,
                    Set.of("current-request"), Map.of("current-request", 1.0),
                    new WorkspaceScoringPolicy(0.0, 1.0),
                    List.of(new WorkspaceCandidate("request-context", SCOPE,
                            WorkspaceCandidateKind.OBSERVATION, 2.0, 1.0, 0.0,
                            2, Set.of("current-request"), Set.of(), true,
                            List.of(new EvidenceReference("request", "request:test")))), at,
                    workspaceAssessmentReferenceOverride == null
                            ? context.requireUncertaintyAssessmentReference() : workspaceAssessmentReferenceOverride);
        }
        @Override public Set<String> workspaceConsumers(CognitiveCycleOperations.CycleContext context) {
            return Set.of("planner");
        }
        @Override public void recordWorkspaceBroadcast(CognitiveCycleOperations.CycleContext context,
                                                        WorkspaceBroadcastReport report, Instant at) {
            broadcastReport = report;
        }
        @Override public PlanningRequest planningRequest(CognitiveCycleOperations.CycleContext context, Instant at) {
            if (planningRequest == null) throw new UnsupportedOperationException("planning request is not part of this test");
            return planningRequest;
        }
    }

    private static final class EmptyWorldModel implements PlanningWorldModel {
        @Override public String configurationFingerprint() { return "empty-world-test-v1"; }
        @Override public PlanningState initialState(PlanningRequest request) {
            throw new UnsupportedOperationException("planning world is not part of these tests");
        }
        @Override public List<dev.supplychain.agent.planning.api.PlanningAction> availableActions(
                PlanningRequest request, PlanningState state) { return List.of(); }
        @Override public dev.supplychain.agent.planning.api.WorldTransition apply(
                PlanningRequest request, PlanningState state,
                dev.supplychain.agent.planning.api.PlanningAction action) {
            return new dev.supplychain.agent.planning.api.WorldTransition.Rejected("NOT_USED", "not used");
        }
    }
}
