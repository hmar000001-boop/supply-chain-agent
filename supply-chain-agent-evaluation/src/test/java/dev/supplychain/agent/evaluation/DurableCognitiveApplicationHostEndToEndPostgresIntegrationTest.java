package dev.supplychain.agent.evaluation;

import dev.supplychain.agent.actions.api.ActionDescriptor;
import dev.supplychain.agent.actions.api.ActionPrincipal;
import dev.supplychain.agent.actions.api.Capability;
import dev.supplychain.agent.actions.api.CapabilityGrant;
import dev.supplychain.agent.actions.api.ResourceReference;
import dev.supplychain.agent.actions.external.grounding.ReserveInventoryRequest;
import dev.supplychain.agent.actions.external.grounding.SupplyChainGroundingActions;
import dev.supplychain.agent.actions.external.grounding.SupplyChainSimulationWorld;
import dev.supplychain.agent.actions.external.perception.Observation;
import dev.supplychain.agent.actions.external.perception.SupplyChainPerceptionPort;
import dev.supplychain.agent.actions.internal.ReasoningAssessment;
import dev.supplychain.agent.actions.internal.ReasoningConfidence;
import dev.supplychain.agent.app.durable.DurableCognitiveApplicationHost;
import dev.supplychain.agent.app.durable.DurableR2Configuration;
import dev.supplychain.agent.core.interpretation.InterpretationOutcome;
import dev.supplychain.agent.core.interpretation.InterpretationRequest;
import dev.supplychain.agent.core.interpretation.RequestInterpretation;
import dev.supplychain.agent.domain.common.Quantity;
import dev.supplychain.agent.domain.disruption.Disruption;
import dev.supplychain.agent.domain.disruption.DisruptionId;
import dev.supplychain.agent.domain.disruption.DisruptionSeverity;
import dev.supplychain.agent.domain.disruption.DisruptionStatus;
import dev.supplychain.agent.domain.disruption.DisruptionType;
import dev.supplychain.agent.domain.inventory.InventoryLotId;
import dev.supplychain.agent.domain.orders.CustomerOrder;
import dev.supplychain.agent.domain.orders.CustomerOrderId;
import dev.supplychain.agent.domain.recovery.RecoveryObjective;
import dev.supplychain.agent.domain.world.SupplyChainSnapshot;
import dev.supplychain.agent.evaluation.catalog.ScenarioCatalog;
import dev.supplychain.agent.evaluation.catalog.ScenarioDefinition;
import dev.supplychain.agent.evaluation.fixture.ScenarioFixture;
import dev.supplychain.agent.evaluation.fixture.ScenarioFixtureFactory;
import dev.supplychain.agent.evaluation.planner.EvaluationPlannerFactory;
import dev.supplychain.agent.infrastructure.postgres.runtime.JsonCodec;
import dev.supplychain.agent.infrastructure.postgres.runtime.ObservationProvenanceResolver;
import dev.supplychain.agent.infrastructure.postgres.runtime.PostgresRuntimeCodecs;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.Provenance;
import dev.supplychain.agent.memory.common.ProvenanceSource;
import dev.supplychain.agent.memory.episodic.EpisodeOutcome;
import dev.supplychain.agent.memory.working.MemoryValue;
import dev.supplychain.agent.memory.working.WorkingMemoryId;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidate;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateReference;
import dev.supplychain.agent.mechanisms.b1.B1CandidateFeatures;
import dev.supplychain.agent.mechanisms.b1.B1CandidateFeatureProvider;
import dev.supplychain.agent.mechanisms.b1.B1ScoringPolicy;
import dev.supplychain.agent.mechanisms.b1.B1SelectionRequest;
import dev.supplychain.agent.mechanisms.b1.B1SelectionResult;
import dev.supplychain.agent.mechanisms.b1.MaintenanceProfile;
import dev.supplychain.agent.mechanisms.b2.ImpasseResolutionCase;
import dev.supplychain.agent.mechanisms.b2.ImpasseSeverity;
import dev.supplychain.agent.mechanisms.b2.ImpasseSignal;
import dev.supplychain.agent.mechanisms.b2.ImpasseType;
import dev.supplychain.agent.mechanisms.b2.ParentContextSnapshot;
import dev.supplychain.agent.mechanisms.b2.ResolutionDisposition;
import dev.supplychain.agent.mechanisms.b2.ResolutionFinding;
import dev.supplychain.agent.mechanisms.b3.ConsumerReceiptStatus;
import dev.supplychain.agent.mechanisms.b3.WorkspaceAdmissionRequest;
import dev.supplychain.agent.mechanisms.b3.WorkspaceCandidate;
import dev.supplychain.agent.mechanisms.b3.WorkspaceCandidateKind;
import dev.supplychain.agent.mechanisms.b3.WorkspaceConsumer;
import dev.supplychain.agent.mechanisms.b3.WorkspaceConsumerReceipt;
import dev.supplychain.agent.mechanisms.b3.WorkspaceScoringPolicy;
import dev.supplychain.agent.mechanisms.b4.DelegationReturn;
import dev.supplychain.agent.mechanisms.b4.IntentionStatus;
import dev.supplychain.agent.mechanisms.b4.ObjectiveSatisfactionProof;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntention;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntentionId;
import dev.supplychain.agent.mechanisms.b4.RecoveryMethodReference;
import dev.supplychain.agent.mechanisms.b6.ResourceBudget;
import dev.supplychain.agent.mechanisms.b6.ResourceUnit;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment;
import dev.supplychain.agent.mechanisms.b6.UncertaintyDimension;
import dev.supplychain.agent.mechanisms.b6.UncertaintySignal;
import dev.supplychain.agent.runtime.cognitive.lifecycle.ActionDispatchCommand;
import dev.supplychain.agent.planning.api.DeterministicCandidateEvaluator;
import dev.supplychain.agent.planning.api.PlanningWorldModel;
import dev.supplychain.agent.planning.api.PlanningAction;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningStrategy;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations.HumanControlResolution;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations.EvidenceRetrieved;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleState;
import dev.supplychain.agent.runtime.cognitive.ResourceBudgetExecutionGuard;
import dev.supplychain.agent.runtime.cognitive.decision.CognitiveCycleDecisionPort;
import dev.supplychain.agent.runtime.cognitive.decision.CognitiveCycleDecisionServices;
import dev.supplychain.agent.runtime.cognitive.lifecycle.ActionExecutionBatch;
import dev.supplychain.agent.runtime.cognitive.lifecycle.AuthorizedActionPlan;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleAuthorityPort;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleAuthorityPort.ApprovalBinding;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort.ChildInvestigationPlan;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort.ChildInvestigationStep;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort.ContextFact;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort.EpisodeDraft;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort.ExternalEvidenceDecision;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort.ImpasseOpenRequest;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort.IntentionDirective;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort.IntentionSeed;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort.MetaControlInputs;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort;
import dev.supplychain.agent.runtime.langchain4j.BudgetedReasoningProvider;
import dev.supplychain.agent.simulator.SimulationResult;
import dev.supplychain.agent.simulator.SupplyChainSimulator;
import dev.supplychain.agent.simulator.command.SimulationCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GAP-08 acceptance: the durable application (not the in-memory evaluation host) completes
 * approval, dispatch, reconciliation and B4 completion across a reconstructed host instance,
 * then completes a second top-level cycle that consumes the first cycle's persisted observation.
 * The test deliberately keeps external simulator/authority adapters alive while rebuilding the
 * application host; it is a host-instance restart test, not a forked-JVM process-restart test.
 */
@EnabledIfSystemProperty(named = "phase0.postgres", matches = "true")
class DurableCognitiveApplicationHostEndToEndPostgresIntegrationTest {
    private static final Instant NOW = ScenarioFixtureFactory.FIXTURE_AS_OF;
    private static final String FIRST_EXECUTION = "gap08-durable-cycle-1";
    private static final String SECOND_EXECUTION = "gap08-durable-cycle-2";
    private static final Pattern TOKEN = Pattern.compile("\\\"token\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    @Test
    void durableHostApprovesDispatchesReconcilesRestartsAndConsumesPriorCycleMemory() throws Exception {
        assertRegistryCodecRoundTripIsStable();
        assertAuthorizedPlanCodecUsesValueEquality();
        MemoryScopeId scope = new MemoryScopeId("m8-gap08-e2e-" + UUID.randomUUID());
        ScenarioCatalog catalog = ScenarioCatalog.loadDefault();
        ScenarioDefinition authorizedWrite = catalog.require("REQ-09");
        ScenarioFixtureFactory fixtureFactory = new ScenarioFixtureFactory(catalog);
        ScenarioFixture first = fixtureFactory.create(withId(authorizedWrite, "GAP08-CYCLE-ONE"), null, scope);
        ScenarioFixture second = fixtureFactory.create(withId(authorizedWrite, "GAP08-CYCLE-TWO"), null, scope);
        FixtureDirectory fixtures = new FixtureDirectory(Map.of(FIRST_EXECUTION, first, SECOND_EXECUTION, second));
        SupplyChainSnapshot initialWorld = withDisruptions(
                merge(first.planningRequest().initialWorld(), second.planningRequest().initialWorld()), first, second);
        assertTrue(initialWorld.disruptions().containsKey(disruptionId(first)));
        assertTrue(initialWorld.disruptions().containsKey(disruptionId(second)));
        MutableSimulationWorld simulationWorld = new MutableSimulationWorld(initialWorld);
        UncertaintyDirectory uncertainty = new UncertaintyDirectory();
        AtomicReference<String> firstObservationId = new AtomicReference<>();
        AtomicBoolean secondCycleConsumedPriorObservation = new AtomicBoolean();
        AtomicReference<ActionExecutionBatch> uncertainFirstBatch = new AtomicReference<>();
        DecisionAdapter decision = new DecisionAdapter(fixtures, simulationWorld, uncertainty,
                secondCycleConsumedPriorObservation);
        LifecycleAdapter lifecycle = new LifecycleAdapter(fixtures, simulationWorld, uncertainty,
                firstObservationId, secondCycleConsumedPriorObservation, uncertainFirstBatch);
        ApprovalAuthority authority = new ApprovalAuthority(fixtures);
        DurableCognitiveApplicationHost.Ports ports = ports(scope, fixtures, fixtureFactory, simulationWorld,
                decision, lifecycle, authority);
        Map<String, String> environment = Map.copyOf(System.getenv());
        String firstApprovalId;
        String firstThread;

        // First host instance: execute all cognition and planning, but stop at the exact plan-bound approval boundary.
        try (DurableCognitiveApplicationHost firstHost = DurableCognitiveApplicationHost.open(environment, ports)) {
            decision.attach(firstHost);
            lifecycle.attach(firstHost);
            var pending = firstHost.start(new DurableCognitiveApplicationHost.CycleRequest(
                    "gap08-request-1", FIRST_EXECUTION, scope,
                    "Recover the authorized-write disruption for the first affected customer order."))
                    .orElseThrow(() -> new AssertionError("first durable cycle produced no checkpoint"));
            assertTrue(pending.interrupted(), "a domain-write plan must suspend for explicit approval");
            firstApprovalId = pending.state().text(CognitiveCycleState.APPROVAL_ID);
            assertFalse(firstApprovalId.isBlank(), "approval identity must be checkpointed");
            assertEquals("", pending.state().text(CognitiveCycleState.APPROVAL_RESPONSE_ID));
            assertTrue(pending.state().text(CognitiveCycleState.SELECTED_CANDIDATE_ID).isBlank() == false);
            assertEquals(0, simulationWorld.transitionCount(),
                    "the durable host must not mutate the simulator before plan-bound approval");
            assertEquals(0, simulationWorld.currentSnapshot().version());
            assertTrue(authority.isPending(firstApprovalId));
            firstThread = firstHost.threadId(scope.value(), FIRST_EXECUTION);
        }

        // The external authority adapter remains available while the application host is closed.
        String firstResponse = "verified-approval-response-1";
        String firstApprover = "authenticated-operator-1";
        authority.approve(firstApprovalId, firstResponse, firstApprover);

        // The external simulator commits the effect but loses its acknowledgement. The action
        // ledger must classify this as UNKNOWN_OUTCOME; the application must reconcile, not retry.
        simulationWorld.loseNextAcknowledgementAfterApply();
        try (DurableCognitiveApplicationHost effectHost = DurableCognitiveApplicationHost.open(environment, ports)) {
            decision.attach(effectHost);
            lifecycle.attach(effectHost);
            assertEquals(firstThread, effectHost.threadId(scope.value(), FIRST_EXECUTION));
            Exception interruption = assertThrows(Exception.class, () -> effectHost.resume(scope.value(), FIRST_EXECUTION,
                    new DurableCognitiveApplicationHost.ApprovalReply(firstResponse, firstApprover)),
                    "the injected application interruption must occur after the simulator effect and before reconciliation");
            assertTrue(hasCauseContaining(interruption,
                    "GAP-08 simulated host interruption after the external effect and before reconciliation"),
                    "the run must stop at the deliberately injected effect-boundary interruption, not an earlier unrelated failure: "
                            + interruption);
            assertEquals(1, simulationWorld.transitionCount(), "the simulator effect must have occurred exactly once");
            assertEquals(1, simulationWorld.currentSnapshot().version());
            ActionExecutionBatch uncertainBatch = uncertainFirstBatch.get();
            assertNotNull(uncertainBatch, "the effect-boundary interruption must observe the durable UNKNOWN_OUTCOME batch");
            assertEquals(ActionExecutionBatch.BatchStatus.UNKNOWN_OUTCOME, uncertainBatch.status());
            assertEquals(1, uncertainBatch.items().size());
            assertTrue(uncertainBatch.items().getFirst().outcomeUnknown());
        }

        // Rebuild the application host from PostgreSQL and resume at reconciliation. The external
        // simulator and approval authority adapters remain outside the host instance, as real systems do.
        String firstObservation;
        try (DurableCognitiveApplicationHost reconciledHost = DurableCognitiveApplicationHost.open(environment, ports)) {
            decision.attach(reconciledHost);
            lifecycle.attach(reconciledHost);
            var completedFirst = reconciledHost.resume(scope.value(), FIRST_EXECUTION,
                    new DurableCognitiveApplicationHost.ApprovalReply(firstResponse, firstApprover))
                    .orElseThrow(() -> new AssertionError("post-restart reconciliation produced no result"));
            assertFalse(completedFirst.interrupted(), "reconciled first cycle must complete without redispatch");
            assertEquals("COMPLETED", completedFirst.state().text(CognitiveCycleState.DECISION_STATUS));
            assertEquals("APPROVED", completedFirst.state().text(CognitiveCycleState.APPROVAL_DECISION));
            assertEquals("OBJECTIVE_SATISFIED", completedFirst.state().text(CognitiveCycleState.OBSERVATION_DISPOSITION));
            assertEquals(1, simulationWorld.transitionCount(), "reconciliation after restart must not duplicate the external effect");
            assertEquals(1, simulationWorld.currentSnapshot().version());

            firstObservation = completedFirst.state().text(CognitiveCycleState.OBSERVATION_ID);
            assertFalse(firstObservation.isBlank());
            firstObservationId.set(firstObservation);
            var firstMemory = reconciledHost.workingMemory().load(new WorkingMemoryId("working-memory:" + FIRST_EXECUTION));
            assertTrue(firstMemory.values().containsKey("observation:" + firstObservation),
                    "reconciled observation must be persisted in the first cycle's CoALA working memory");
            var firstIntention = reconciledHost.runtimePersistence().recoveryIntentions()
                    .findById(new RecoveryIntentionId("intention:" + FIRST_EXECUTION)).orElseThrow();
            assertEquals(IntentionStatus.COMPLETED, firstIntention.status(),
                    "B4 must complete only after the simulator-backed satisfaction proof is reconciled");
            assertTrue(lifecycle.metaControlDecisions.containsKey(FIRST_EXECUTION), "B6 meta-control must run on the first cycle");
            assertEquals(0, lifecycle.escalations.get(), "a successful end-to-end cycle must not take the escalation path");
            ActionExecutionBatch reconciledUnknown = reconciledHost.runtimePersistence().actionExecutionBatches()
                    .find(uncertainBatchId(uncertainFirstBatch.get())).orElseThrow();
            assertEquals(ActionExecutionBatch.BatchStatus.UNKNOWN_OUTCOME, reconciledUnknown.status(),
                    "the transport-level ledger remains unknown; authoritative simulator evidence resolves the business outcome");
            assertTrue(reconciledUnknown.items().getFirst().outcomeUnknown());

            // A different, independently identified disruption shares the tenant memory scope.
            // Its context adapter imports the exact prior observation from PostgreSQL before planning.
            var pendingSecond = reconciledHost.start(new DurableCognitiveApplicationHost.CycleRequest(
                    "gap08-request-2", SECOND_EXECUTION, scope,
                    "Assess and recover the second authorized-write disruption using prior-cycle context."))
                    .orElseThrow(() -> new AssertionError("second durable cycle produced no checkpoint"));
            assertTrue(pendingSecond.interrupted(), "second domain-write cycle must independently require approval");
            assertEquals(1, simulationWorld.transitionCount(), "second cycle must not dispatch before its own approval");
            assertTrue(secondCycleConsumedPriorObservation.get(),
                    "the second cycle's planner must consume the first cycle's persisted observation");
            String secondApprovalId = pendingSecond.state().text(CognitiveCycleState.APPROVAL_ID);
            assertFalse(secondApprovalId.isBlank());
            assertNotEquals(firstApprovalId, secondApprovalId);
            String secondResponse = "verified-approval-response-2";
            String secondApprover = "authenticated-operator-2";
            authority.approve(secondApprovalId, secondResponse, secondApprover);

            var completedSecond = reconciledHost.resume(scope.value(), SECOND_EXECUTION,
                    new DurableCognitiveApplicationHost.ApprovalReply(secondResponse, secondApprover))
                    .orElseThrow(() -> new AssertionError("second approval resume produced no result"));
            assertFalse(completedSecond.interrupted());
            assertEquals("COMPLETED", completedSecond.state().text(CognitiveCycleState.DECISION_STATUS));
            assertEquals("APPROVED", completedSecond.state().text(CognitiveCycleState.APPROVAL_DECISION));
            assertEquals("OBJECTIVE_SATISFIED", completedSecond.state().text(CognitiveCycleState.OBSERVATION_DISPOSITION));
            assertEquals(2, simulationWorld.transitionCount(), "two independently approved cycles must produce two effects");
            assertEquals(2, simulationWorld.currentSnapshot().version());

            String secondObservation = completedSecond.state().text(CognitiveCycleState.OBSERVATION_ID);
            assertNotEquals(firstObservation, secondObservation);
            var secondMemory = reconciledHost.workingMemory().load(new WorkingMemoryId("working-memory:" + SECOND_EXECUTION));
            assertTrue(secondMemory.values().containsKey("prior-cycle-observation"));
            assertTrue(secondMemory.values().containsKey("observation:" + secondObservation));
            var secondIntention = reconciledHost.runtimePersistence().recoveryIntentions()
                    .findById(new RecoveryIntentionId("intention:" + SECOND_EXECUTION)).orElseThrow();
            assertEquals(IntentionStatus.COMPLETED, secondIntention.status());
            assertTrue(lifecycle.metaControlDecisions.containsKey(SECOND_EXECUTION), "B6 meta-control must run on the second cycle");
            assertEquals(0, lifecycle.escalations.get(), "both end-to-end cycles must avoid escalation");
            assertSuccessfulBatch(reconciledHost, completedSecond.state());
        }
    }

    private static boolean hasCauseContaining(Throwable failure, String fragment) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(fragment)) return true;
        }
        return false;
    }

    private static String uncertainBatchId(ActionExecutionBatch batch) {
        assertNotNull(batch, "expected a durable batch created before the simulated host interruption");
        return batch.batchId();
    }

    private static void assertSuccessfulBatch(DurableCognitiveApplicationHost host, CognitiveCycleState state) {
        String batchId = state.text(CognitiveCycleState.EXECUTION_OPERATION_ID);
        assertFalse(batchId.isBlank(), "completed graph state must reference the durable action batch");
        ActionExecutionBatch batch = host.runtimePersistence().actionExecutionBatches().find(batchId).orElseThrow();
        assertEquals(ActionExecutionBatch.BatchStatus.SUCCEEDED, batch.status());
        assertFalse(batch.items().isEmpty());
        assertTrue(batch.items().stream().allMatch(item -> item.status() == dev.supplychain.agent.actions.api.ActionStatus.SUCCEEDED
                        || item.status() == dev.supplychain.agent.actions.api.ActionStatus.ALREADY_COMPLETED),
                "every dispatched action must have a successful idempotent receipt");
    }

    private static ScenarioDefinition withId(ScenarioDefinition source, String id) {
        return new ScenarioDefinition(id, source.kind(), source.scenario(), source.setup(),
                source.expectedInvariantOrBehavior(), source.comparatorsOrControls(), source.primaryMeasurements(),
                source.status(), source.fixtureVersion(), source.fixtureProfile(), source.expectedEffectBoundary(),
                source.seed(), source.budgetProfile());
    }

    private static SupplyChainSnapshot withDisruptions(SupplyChainSnapshot snapshot,
            ScenarioFixture first, ScenarioFixture second) {
        SupplyChainSnapshot.Builder builder = snapshot.toBuilder();
        builder.disruption(disruption(first));
        builder.disruption(disruption(second));
        return builder.build();
    }

    private static Disruption disruption(ScenarioFixture fixture) {
        RecoveryObjective objective = fixture.planningRequest().objective();
        Set<String> affected = objective.affectedOrders().stream()
                .map(orderId -> "customer-order:" + orderId.value())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new Disruption(disruptionId(fixture), DisruptionType.COMPOSITE,
                DisruptionSeverity.HIGH, DisruptionStatus.OPEN,
                "Externally reported supply-chain disruption requires a human-approved inventory recovery for "
                        + objective.id().value(), NOW, affected);
    }

    private static DisruptionId disruptionId(ScenarioFixture fixture) {
        return new DisruptionId("GAP08-DISRUPTION-" + fixture.definition().scenarioId());
    }

    private static SupplyChainSnapshot merge(SupplyChainSnapshot first, SupplyChainSnapshot second) {
        SupplyChainSnapshot.Builder builder = first.toBuilder();
        second.products().values().forEach(builder::product);
        second.substitutions().values().forEach(builder::substitution);
        second.suppliers().values().forEach(builder::supplier);
        second.locations().values().forEach(builder::location);
        second.inventoryLots().values().forEach(builder::inventoryLot);
        second.customerOrders().values().forEach(builder::customerOrder);
        second.purchaseOrders().values().forEach(builder::purchaseOrder);
        second.carriers().values().forEach(builder::carrier);
        second.routes().values().forEach(builder::route);
        second.shipments().values().forEach(builder::shipment);
        second.reservations().values().forEach(builder::reservation);
        second.disruptions().values().forEach(builder::disruption);
        second.recoveryObjectives().values().forEach(builder::recoveryObjective);
        second.recoveryCandidates().values().forEach(builder::recoveryCandidate);
        return builder.build();
    }

    private static DurableCognitiveApplicationHost.Ports ports(MemoryScopeId scope, FixtureDirectory fixtures,
            ScenarioFixtureFactory fixtureFactory, MutableSimulationWorld world, DecisionAdapter decision,
            LifecycleAdapter lifecycle, ApprovalAuthority authority) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        List<PlanningStrategy> planners = new EvaluationPlannerFactory(fixtureFactory)
                .strategiesForFullGraph(fixtures.require(FIRST_EXECUTION));
        EnumMap<ResourceUnit, Long> limits = new EnumMap<>(ResourceUnit.class);
        for (ResourceUnit unit : ResourceUnit.values()) limits.put(unit, 100_000L);
        ResourceBudget budget = new ResourceBudget(limits, NOW.plus(Duration.ofDays(1)));

        JsonCodec<AuthorizedActionPlan> authorizedActionPlanCodec = valueRegistryCodec();
        PostgresRuntimeCodecs runtimeCodecs = PostgresRuntimeCodecs.withDefaults(
                registryCodec(), authorizedActionPlanCodec, registryCodec(), registryCodec(),
                ignored -> new ObservationProvenanceResolver.ObservationProvenance(
                        "gap08-test-execution", "gap08-test-principal", "simulator", NOW),
                registryCodec(), registryCodec(), registryCodec(), registryCodec(), registryCodec());
        DurableR2Configuration r2 = new DurableR2Configuration(
                (memoryScope, evidence, at) -> false,
                (draft, approver, approval, at) -> false,
                (context, resolved, result, parent, at) -> Optional.empty(),
                (memoryScope, receiptId, candidateId, signal, evidence, at) -> false,
                "gap08-durable-admission-v1", "gap08-durable-outcome-verifier-v1", null, null);

        WorkspaceConsumer plannerConsumer = broadcast -> new WorkspaceConsumerReceipt(
                "planner", broadcast.snapshot().id(), ConsumerReceiptStatus.ACCEPTED,
                broadcast.snapshot().entries().stream().map(entry -> entry.candidate().id()).toList(),
                broadcast.broadcastAt(), "deterministic durable acceptance planner consumer acknowledged workspace");
        SupplyChainPerceptionPort perception = new SupplyChainPerceptionPort() {
            @Override public Observation<dev.supplychain.agent.domain.supplier.Supplier> supplier(
                    dev.supplychain.agent.domain.supplier.SupplierId id, Instant readAt) {
                throw new UnsupportedOperationException("perception is not used by this inventory-recovery acceptance scenario");
            }
            @Override public Observation<dev.supplychain.agent.domain.inventory.InventoryLot> inventoryLot(
                    InventoryLotId id, Instant readAt) {
                throw new UnsupportedOperationException("perception is not used by this inventory-recovery acceptance scenario");
            }
            @Override public Observation<CustomerOrder> customerOrder(CustomerOrderId id, Instant readAt) {
                throw new UnsupportedOperationException("perception is not used by this inventory-recovery acceptance scenario");
            }
            @Override public Observation<dev.supplychain.agent.domain.logistics.Shipment> shipment(
                    dev.supplychain.agent.domain.logistics.ShipmentId id, Instant readAt) {
                throw new UnsupportedOperationException("perception is not used by this inventory-recovery acceptance scenario");
            }
            @Override public Observation<Quantity> availableInventory(InventoryLotId id, Instant readAt) {
                throw new UnsupportedOperationException("perception is not used by this inventory-recovery acceptance scenario");
            }
        };
        PlanningWorldModel worldModel = fixtureFactory.worldModel();
        DeterministicCandidateEvaluator evaluator = fixtureFactory.evaluator(worldModel);
        return DurableCognitiveApplicationHost.Ports.productionDefaults(scope, decision, lifecycle, authority,
                request -> new RequestInterpretation(InterpretationOutcome.READY, request.originalText(),
                        "Deterministic test interpreter accepted the supplied disruption request."),
                worldModel, evaluator, planners, clock,
                guard -> new BudgetedReasoningProvider(request -> new ReasoningAssessment(
                        "No model invocation is needed for the deterministic acceptance plan.", List.of(), List.of(),
                        List.of(), request.evidence(), List.of(), ReasoningConfidence.UNASSESSED,
                        "The graph uses deterministic evidence and planners in this integration test."), guard),
                perception, world, Map.of("planner", plannerConsumer), ignored -> budget, runtimeCodecs,
                registryCodec(), registryCodec(), registryCodec(), registryCodec(), r2);
    }

    private static void assertRegistryCodecRoundTripIsStable() {
        JsonCodec<StringBuilder> codec = registryCodec();
        StringBuilder value = new StringBuilder("same logical test value");
        String firstEncoding = codec.encode(value);
        assertEquals(firstEncoding, codec.encode(value),
                "re-encoding the same persisted object must retain its opaque registry token");
        assertSame(value, codec.decode(firstEncoding),
                "the same-process test codec must resolve the token to the exact registered object");
        assertEquals(firstEncoding, codec.encode(codec.decode(firstEncoding)),
                "the PostgreSQL read/verify round trip must reproduce the exact payload fingerprint");
    }

    /**
     * Opaque same-process registry adapter for this PostgreSQL integration test only. The
     * production JsonCodec contract requires an allow-listed typed JSON mapper with deterministic
     * field ordering. This test adapter deliberately does not claim cross-process serialization:
     * it assigns one opaque token per object identity and resolves that token to the same object.
     *
     * Runtime artifact stores decode and re-encode payloads before validating their fingerprints.
     * A token derived from Object.toString() is not a canonical representation of a planning
     * artifact graph: nested snapshots, collections and mutable test values do not have a stable
     * JSON field-order contract. Assigning the token once per object identity makes the test
     * adapter's encode(decode(encode(value))) operation stable without weakening store checks.
     */
    private static <T> JsonCodec<T> registryCodec() {
        Map<String, T> registry = new ConcurrentHashMap<>();
        Map<T, String> tokenByIdentity = new IdentityHashMap<>();
        return new JsonCodec<>() {
            @Override public String encode(T value) {
                java.util.Objects.requireNonNull(value, "codec value");
                String token;
                synchronized (tokenByIdentity) {
                    token = tokenByIdentity.get(value);
                    if (token == null) {
                        token = UUID.randomUUID().toString().replace("-", "");
                        tokenByIdentity.put(value, token);
                        registry.put(token, value);
                    }
                }
                return "{\"token\":\"" + token + "\"}";
            }
            @Override public T decode(String json) {
                Matcher matcher = TOKEN.matcher(json);
                if (!matcher.find()) throw new IllegalArgumentException("GAP-08 test codec token is missing");
                T value = registry.get(matcher.group(1));
                if (value == null) throw new IllegalStateException("GAP-08 same-process test codec cannot resolve token");
                return value;
            }
        };
    }

    private record ValueCodecProbe(String identity, List<String> content) { }

    private static void assertAuthorizedPlanCodecUsesValueEquality() {
        JsonCodec<ValueCodecProbe> codec = valueRegistryCodec();
        ValueCodecProbe first = new ValueCodecProbe("authorized-plan", List.of("execution", "candidate", "intent"));
        ValueCodecProbe equivalent = new ValueCodecProbe("authorized-plan", List.of("execution", "candidate", "intent"));
        assertNotSame(first, equivalent, "the idempotency contract must cover separately reconstructed values");
        String firstPayload = codec.encode(first);
        String equivalentPayload = codec.encode(equivalent);
        assertEquals(firstPayload, equivalentPayload,
                "equal immutable artifacts reconstructed on approval/resume must retain one payload fingerprint");
        assertSame(first, codec.decode(equivalentPayload),
                "the same-process registry must resolve a value-equivalent artifact to its original immutable value");
    }

    /**
     * Value-equality registry codec for immutable records that are deliberately reconstructed
     * on each authorization boundary. In particular, CognitiveCycleLifecycleServices.actionPlan()
     * creates a fresh AuthorizedActionPlan in prepareAuthorization, resolveApproval,
     * revalidatePlan and executeAuthorizedCandidate, while all those saves address the same
     * immutable (execution, candidate-set, candidate) key. An identity-only codec would assign a
     * fresh token to each reconstructed record and falsely look like content mutation.
     */
    private static <T> JsonCodec<T> valueRegistryCodec() {
        Map<String, T> registry = new ConcurrentHashMap<>();
        Map<T, String> tokenByValue = new ConcurrentHashMap<>();
        return new JsonCodec<>() {
            @Override public String encode(T value) {
                java.util.Objects.requireNonNull(value, "codec value");
                String token = tokenByValue.computeIfAbsent(value,
                        ignored -> UUID.randomUUID().toString().replace("-", ""));
                registry.putIfAbsent(token, value);
                return "{\"token\":\"" + token + "\"}";
            }
            @Override public T decode(String json) {
                Matcher matcher = TOKEN.matcher(json);
                if (!matcher.find()) throw new IllegalArgumentException("GAP-08 test codec token is missing");
                T value = registry.get(matcher.group(1));
                if (value == null) throw new IllegalStateException("GAP-08 same-process test codec cannot resolve token");
                return value;
            }
        };
    }

    private static final class FixtureDirectory {
        private final Map<String, ScenarioFixture> byExecution;
        private FixtureDirectory(Map<String, ScenarioFixture> byExecution) { this.byExecution = Map.copyOf(byExecution); }
        ScenarioFixture require(String executionId) {
            ScenarioFixture fixture = byExecution.get(executionId);
            if (fixture == null) throw new IllegalArgumentException("unknown GAP-08 execution: " + executionId);
            return fixture;
        }
    }

    private static final class MutableSimulationWorld implements SupplyChainSimulationWorld {
        private final AtomicReference<SupplyChainSnapshot> snapshot;
        private final SupplyChainSimulator simulator = new SupplyChainSimulator();
        private final AtomicInteger transitions = new AtomicInteger();
        private final AtomicBoolean loseAcknowledgementAfterApply = new AtomicBoolean();
        private MutableSimulationWorld(SupplyChainSnapshot initial) { snapshot = new AtomicReference<>(initial); }
        void loseNextAcknowledgementAfterApply() { loseAcknowledgementAfterApply.set(true); }
        @Override public SupplyChainSnapshot currentSnapshot() { return snapshot.get(); }
        @Override public synchronized SimulationResult apply(long expectedVersion, SimulationCommand command) {
            SupplyChainSnapshot current = snapshot.get();
            if (current.version() != expectedVersion) {
                throw new IllegalStateException("GAP-08 simulator version conflict: expected " + expectedVersion
                        + " but current version is " + current.version());
            }
            SimulationResult result = simulator.simulate(current, command);
            snapshot.set(result.snapshot());
            transitions.incrementAndGet();
            if (loseAcknowledgementAfterApply.compareAndSet(true, false)) {
                throw new IllegalStateException("GAP-08 injected transport interruption after simulator commit and before action receipt");
            }
            return result;
        }
        int transitionCount() { return transitions.get(); }
    }

    private static final class UncertaintyDirectory {
        private final Map<String, UncertaintyAssessment> byRequestKey = new ConcurrentHashMap<>();
        private final Map<UncertaintyAssessment.Reference, UncertaintyAssessment> byReference = new ConcurrentHashMap<>();

        UncertaintyAssessment assess(CognitiveCycleOperations.CycleContext context, String requestKey, Instant at) {
            return byRequestKey.computeIfAbsent(requestKey, ignored -> {
                UncertaintyAssessment.Reference previous = context.uncertaintyAssessmentReference();
                long version = previous == null ? 1L : previous.version() + 1L;
                String id = previous == null ? "gap08-uncertainty:" + context.executionId() : previous.assessmentId();
                var reference = new UncertaintyAssessment.Reference(id, version, context.executionId(), context.memoryScopeId());
                var assessment = new UncertaintyAssessment(reference, at, at.plusSeconds(3_600),
                        "gap08-deterministic-assessment:" + requestKey,
                        UncertaintyAssessment.Calibration.notCalibrated(), Map.of(
                        UncertaintyDimension.EVIDENCE, new UncertaintySignal(UncertaintyDimension.EVIDENCE, 0.1, "fixture evidence is explicit", 1),
                        UncertaintyDimension.HYPOTHESIS, new UncertaintySignal(UncertaintyDimension.HYPOTHESIS, 0.1, "fixture hypothesis is bounded", 1),
                        UncertaintyDimension.PLAN, new UncertaintySignal(UncertaintyDimension.PLAN, 0.1, "planner output is deterministic", 1)));
                byReference.put(reference, assessment);
                return assessment;
            });
        }

        UncertaintyAssessment current(CognitiveCycleOperations.CycleContext context, Instant at) {
            UncertaintyAssessment.Reference reference = context.requireUncertaintyAssessmentReference();
            UncertaintyAssessment assessment = byReference.get(reference);
            if (assessment == null || !assessment.isCurrentFor(context.executionId(), context.memoryScopeId(), at)) {
                throw new SecurityException("GAP-08 adapter cannot resolve the exact current uncertainty assessment");
            }
            return assessment;
        }
    }

    private static final class DecisionAdapter implements CognitiveCycleDecisionPort {
        private final FixtureDirectory fixtures;
        private final MutableSimulationWorld world;
        private final UncertaintyDirectory uncertainty;
        private final AtomicBoolean secondCycleConsumedPriorObservation;
        private volatile DurableCognitiveApplicationHost host;

        private DecisionAdapter(FixtureDirectory fixtures, MutableSimulationWorld world,
                UncertaintyDirectory uncertainty, AtomicBoolean secondCycleConsumedPriorObservation) {
            this.fixtures = fixtures;
            this.world = world;
            this.uncertainty = uncertainty;
            this.secondCycleConsumedPriorObservation = secondCycleConsumedPriorObservation;
        }

        void attach(DurableCognitiveApplicationHost host) { this.host = host; }
        @Override public MemoryScopeId memoryScope(CognitiveCycleOperations.CycleContext context) {
            return new MemoryScopeId(context.memoryScopeId());
        }
        @Override public dev.supplychain.agent.memory.retrieval.MemoryRetrievalQuery retrievalQuery(
                CognitiveCycleOperations.CycleContext context, Instant at) {
            if (context.workingMemoryId().isBlank()) throw new IllegalStateException("B1 retrieval requires the active working-memory identity");
            return new dev.supplychain.agent.memory.retrieval.MemoryRetrievalQuery(memoryScope(context), null, null,
                    new WorkingMemoryId(context.workingMemoryId()), at, at, 20);
        }
        @Override public B1SelectionRequest b1SelectionRequest(CognitiveCycleOperations.CycleContext context, Instant at) {
            return new B1SelectionRequest(memoryScope(context), Set.of("inventory", "order", "disruption"), at,
                    100, 20, Duration.ofDays(7), new B1ScoringPolicy(0.0, 0.0, 0.0, 0.0));
        }
        @Override public B1CandidateFeatureProvider b1FeatureProvider(
                CognitiveCycleOperations.CycleContext context) {
            return ignored -> new B1CandidateFeatures(List.of(), Map.of(), 1.0, 1, Duration.ZERO,
                    new MaintenanceProfile(0.0, 0.0, 0.0, Duration.ofDays(1), NOW.minusSeconds(1)));
        }
        private final Map<String, List<B1MemoryCandidate>> selectedByExecution = new ConcurrentHashMap<>();

        @Override public EvidenceRetrieved persistEvidenceSelection(CognitiveCycleOperations.CycleContext context,
                dev.supplychain.agent.memory.retrieval.MemoryRetrievalResult retrieved,
                B1SelectionResult selected, Instant at) {
            var activeWorking = retrieved.workingMemory().orElseThrow(() ->
                    new IllegalStateException("B1 did not retrieve the active PostgreSQL working-memory revision"));
            if (!activeWorking.id().value().equals(context.workingMemoryId())
                    || activeWorking.version() != context.workingMemoryVersion()
                    || !activeWorking.scopeId().equals(memoryScope(context))) {
                throw new SecurityException("B1 retrieval did not return the exact scoped working-memory revision");
            }
            if (selected.selectedCandidates().stream().noneMatch(candidate -> candidate.kind()
                    == dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateKind.WORKING
                    && candidate.memoryReference().equals("working:" + activeWorking.id().value() + "@" + activeWorking.version()))) {
                throw new IllegalStateException("B1 did not select the exact active CoALA working-memory revision");
            }
            String disruptionKey = "disruption:" + disruptionId(fixtures.require(context.executionId())).value();
            if (!activeWorking.values().containsKey(disruptionKey)) {
                throw new IllegalStateException("B1 did not retrieve the disruption fact persisted in the active CoALA working-memory revision");
            }
            selectedByExecution.put(context.executionId(), selected.selectedCandidates());
            return new CognitiveCycleOperations.EvidenceRetrieved(CognitiveCycleOperations.EvidenceDisposition.READY,
                    "gap08-evidence-set:" + context.executionId(), 0.1,
                    "B1 selected the exact active PostgreSQL working-memory revision for this versioned evidence set");
        }

        @Override public List<B1MemoryCandidate> selectedB1MemoryCandidates(CognitiveCycleOperations.CycleContext context, Instant at) {
            return selectedByExecution.getOrDefault(context.executionId(), List.of());
        }

        @Override public Map<String, List<B1MemoryCandidateReference>> workspaceCandidateMemoryAttributions(
                CognitiveCycleOperations.CycleContext context, Instant at) {
            List<B1MemoryCandidate> selected = selectedB1MemoryCandidates(context, at);
            if (selected.isEmpty()) throw new IllegalStateException("B3 cannot attribute its workspace candidate without the persisted B1 selection");
            List<B1MemoryCandidateReference> references = selected.stream().map(DecisionAdapter::referenceOf).toList();
            return Map.of("request-context:" + context.requestId(), references);
        }

        private static B1MemoryCandidateReference referenceOf(B1MemoryCandidate candidate) {
            int separator = candidate.memoryReference().indexOf(':');
            int revisionSeparator = candidate.memoryReference().lastIndexOf('@');
            if (separator < 1 || revisionSeparator <= separator + 1) {
                throw new IllegalStateException("B1 memory candidate reference is malformed");
            }
            return new B1MemoryCandidateReference(candidate.kind(), candidate.scopeId(),
                    candidate.memoryReference().substring(separator + 1, revisionSeparator),
                    Long.parseLong(candidate.memoryReference().substring(revisionSeparator + 1)));
        }
        @Override public UncertaintyAssessment currentUncertaintyAssessment(CognitiveCycleOperations.CycleContext context, Instant at) {
            return uncertainty.current(context, at);
        }
        @Override public WorkspaceAdmissionRequest workspaceAdmissionRequest(CognitiveCycleOperations.CycleContext context, Instant at) {
            MemoryScopeId scope = memoryScope(context);
            var candidate = new WorkspaceCandidate("request-context:" + context.requestId(), scope,
                    WorkspaceCandidateKind.OBSERVATION, 2.0, 1.0, 0.0, 2,
                    Set.of("current-request"), Set.of(), true,
                    List.of(new EvidenceReference("request", context.requestId())));
            return new WorkspaceAdmissionRequest("gap08-cycle:" + context.executionId(), scope, 10, 5,
                    Set.of("current-request"), Map.of("current-request", 1.0),
                    new WorkspaceScoringPolicy(0.0, 1.0), List.of(candidate), at,
                    context.requireUncertaintyAssessmentReference());
        }
        @Override public Set<String> workspaceConsumers(CognitiveCycleOperations.CycleContext context) { return Set.of("planner"); }
        @Override public void recordWorkspaceBroadcast(CognitiveCycleOperations.CycleContext context,
                dev.supplychain.agent.mechanisms.b3.WorkspaceBroadcastReport report, Instant at) {
            if (!report.allAccepted()) throw new IllegalStateException("GAP-08 planner workspace consumer did not acknowledge every admitted item");
        }
        @Override public PlanningRequest planningRequest(CognitiveCycleOperations.CycleContext context, Instant at) {
            DurableCognitiveApplicationHost currentHost = requireHost();
            ScenarioFixture fixture = fixtures.require(context.executionId());
            var working = currentHost.workingMemory().load(new WorkingMemoryId(context.workingMemoryId()));
            if (!working.scopeId().equals(memoryScope(context)) || working.version() != context.workingMemoryVersion()) {
                throw new IllegalStateException("GAP-08 planner received a stale or cross-scope working-memory snapshot");
            }
            if (context.executionId().equals(SECOND_EXECUTION)) {
                if (!working.values().containsKey("prior-cycle-observation")) {
                    throw new IllegalStateException("second cycle did not load the first cycle's persisted observation");
                }
                secondCycleConsumedPriorObservation.set(true);
            }
            ActionDescriptor reserve = currentHost.actionRegistry().findHandler(SupplyChainGroundingActions.ReserveInventory.ID)
                    .orElseThrow().descriptor();
            DisruptionId disruptionId = disruptionId(fixture);
            if (!world.currentSnapshot().disruptions().containsKey(disruptionId)) {
                throw new IllegalStateException("GAP-08 planning request is missing its recorded disruption event");
            }
            List<EvidenceReference> evidence = new ArrayList<>(fixture.planningRequest().evidence());
            evidence.add(new EvidenceReference("disruption", disruptionId.value()));
            return copyRequest(fixture.planningRequest(), context.executionId(), fixture.planningRequest().objective(),
                    world.currentSnapshot(), working, List.of(reserve), evidence,
                    context.requireUncertaintyAssessmentReference(),
                    context.plannerSessionId().isBlank() ? null : context.plannerSessionId(), at);
        }
        private DurableCognitiveApplicationHost requireHost() {
            DurableCognitiveApplicationHost value = host;
            if (value == null) throw new IllegalStateException("GAP-08 decision adapter was not attached to the durable host");
            return value;
        }
    }

    private static final class LifecycleAdapter implements CognitiveCycleLifecyclePort {
        private final FixtureDirectory fixtures;
        private final MutableSimulationWorld world;
        private final UncertaintyDirectory uncertainty;
        private final AtomicReference<String> firstObservationId;
        private final AtomicBoolean secondCycleConsumedPriorObservation;
        private final AtomicReference<ActionExecutionBatch> uncertainFirstBatch;
        private final AtomicBoolean interruptBeforeUnknownOutcomeReconciliation = new AtomicBoolean(true);
        private final AtomicInteger escalations = new AtomicInteger();
        private final Map<String, dev.supplychain.agent.mechanisms.b6.MetaControlDecision> metaControlDecisions = new ConcurrentHashMap<>();
        private volatile DurableCognitiveApplicationHost host;
        private volatile ResourceBudgetExecutionGuard budgetGuard;

        private LifecycleAdapter(FixtureDirectory fixtures, MutableSimulationWorld world,
                UncertaintyDirectory uncertainty, AtomicReference<String> firstObservationId,
                AtomicBoolean secondCycleConsumedPriorObservation,
                AtomicReference<ActionExecutionBatch> uncertainFirstBatch) {
            this.fixtures = fixtures;
            this.world = world;
            this.uncertainty = uncertainty;
            this.firstObservationId = firstObservationId;
            this.secondCycleConsumedPriorObservation = secondCycleConsumedPriorObservation;
            this.uncertainFirstBatch = uncertainFirstBatch;
        }
        void attach(DurableCognitiveApplicationHost host) {
            this.host = host;
            this.budgetGuard = host.resourceBudgetGuard();
        }
        private DurableCognitiveApplicationHost requireHost() {
            DurableCognitiveApplicationHost value = host;
            if (value == null) throw new IllegalStateException("GAP-08 lifecycle adapter was not attached to the durable host");
            return value;
        }
        private ResourceBudgetExecutionGuard requireBudgetGuard() {
            ResourceBudgetExecutionGuard value = budgetGuard;
            if (value == null) throw new IllegalStateException("GAP-08 lifecycle adapter has no host-owned B6 guard");
            return value;
        }
        private ScenarioFixture fixture(CognitiveCycleOperations.CycleContext context) { return fixtures.require(context.executionId()); }
        @Override public MemoryScopeId memoryScope(CognitiveCycleOperations.CycleContext context) { return new MemoryScopeId(context.memoryScopeId()); }
        @Override public WorkingMemoryId workingMemoryId(CognitiveCycleOperations.CycleContext context) {
            return new WorkingMemoryId("working-memory:" + context.executionId());
        }
        @Override public InterpretationRequest interpretationRequest(CognitiveCycleOperations.CycleContext context, Instant at) {
            return new InterpretationRequest(context.request(), context.clarificationContext(), context.clarificationRound());
        }
        @Override public void persistClarification(CognitiveCycleOperations.CycleContext context, Instant at) { }
        @Override public Optional<IntentionSeed> intentionSeed(CognitiveCycleOperations.CycleContext context, Instant at) {
            RecoveryObjective objective = fixture(context).planningRequest().objective();
            return Optional.of(new IntentionSeed(new RecoveryIntentionId("intention:" + context.executionId()), objective,
                    "gap08-durable-application-test", new RecoveryMethodReference("cot", "candidate:initial-method:" + context.executionId())));
        }
        @Override public List<ContextFact> contextFacts(CognitiveCycleOperations.CycleContext context,
                dev.supplychain.agent.memory.working.WorkingMemorySnapshot current, Instant at) {
            List<ContextFact> facts = new ArrayList<>();
            facts.add(new ContextFact("request:current", new MemoryValue.Text(context.request()),
                    List.of(new EvidenceReference("request", context.requestId()))));
            DisruptionId currentDisruptionId = disruptionId(fixture(context));
            Disruption currentDisruption = world.currentSnapshot().disruptions().get(currentDisruptionId);
            if (currentDisruption == null || currentDisruption.status() != DisruptionStatus.OPEN) {
                throw new IllegalStateException("GAP-08 context loading requires the exact current open disruption event");
            }
            facts.add(new ContextFact("disruption:" + currentDisruptionId.value(),
                    new MemoryValue.Text(currentDisruption.description()),
                    List.of(new EvidenceReference("disruption", currentDisruptionId.value()))));
            if (context.executionId().equals(SECOND_EXECUTION)) {
                String previousObservation = Optional.ofNullable(firstObservationId.get())
                        .orElseThrow(() -> new IllegalStateException("first cycle observation is unavailable for second-cycle context"));
                var prior = requireHost().workingMemory().load(new WorkingMemoryId("working-memory:" + FIRST_EXECUTION));
                var priorEntry = prior.values().get("observation:" + previousObservation);
                if (priorEntry == null) throw new IllegalStateException("the first cycle's observation was not persisted in PostgreSQL working memory");
                facts.add(new ContextFact("prior-cycle-observation", priorEntry.value(), priorEntry.evidence()));
                secondCycleConsumedPriorObservation.set(true);
            }
            return List.copyOf(facts);
        }
        @Override public ExternalEvidenceDecision acceptExternalEvidence(CognitiveCycleOperations.CycleContext context, Instant at) {
            return new ExternalEvidenceDecision(CognitiveCycleOperations.EvidenceAcceptance.REJECTED, "", List.of(),
                    "the versioned scenario fixture already supplies the evidence set for this request");
        }
        @Override public String objectiveFingerprint(CognitiveCycleOperations.CycleContext context) {
            return dev.supplychain.agent.mechanisms.b4.ObjectiveFingerprint.from(fixture(context).planningRequest().objective()).value();
        }
        @Override public ImpasseOpenRequest impasseOpenRequest(CognitiveCycleOperations.CycleContext context, Instant at) {
            String reason = context.lastReason().isBlank() ? "No feasible plan under the current fixture." : context.lastReason();
            var signal = new ImpasseSignal(ImpasseType.NO_FEASIBLE_CANDIDATE, ImpasseSeverity.HIGH,
                    "gap08-candidate-set:" + context.executionId(), reason,
                    List.of(new EvidenceReference("scenario-fixture", fixture(context).definition().scenarioId())), at);
            return new ImpasseOpenRequest("gap08-impasse:" + context.executionId(), signal,
                    Set.of(Capability.INVENTORY_READ), Set.of(Capability.INVENTORY_READ), at.plusSeconds(60), 1);
        }
        @Override public CognitiveCycleLifecyclePort.ChildInvestigationPlan planChildInvestigation(
                CognitiveCycleOperations.CycleContext context, ImpasseResolutionCase resolutionCase, Instant at) {
            return new ChildInvestigationPlan(List.of(), ResolutionDisposition.UNRESOLVED, List.of(), DelegationReturn.NO_METHOD_CHANGE);
        }
        @Override public List<ResolutionFinding> executeChildInvestigationStep(CognitiveCycleOperations.CycleContext context,
                ImpasseResolutionCase resolutionCase, ChildInvestigationStep step, Instant at) {
            throw new UnsupportedOperationException("GAP-08 success fixture must not invent child investigation evidence");
        }
        @Override public ParentContextSnapshot currentParentContext(CognitiveCycleOperations.CycleContext context,
                dev.supplychain.agent.memory.working.WorkingMemorySnapshot latestWorkingMemory) {
            return new ParentContextSnapshot(latestWorkingMemory.scopeId(), context.requestId(),
                    latestWorkingMemory.version(), objectiveFingerprint(context));
        }
        @Override public IntentionDirective intentionDirective(CognitiveCycleOperations.CycleContext context,
                RecoveryIntention current, Instant at) {
            return new IntentionDirective(CognitiveCycleOperations.IntentionAction.CONTINUE,
                    "preserve the exact recovery objective throughout approval and execution", null,
                    objectiveFingerprint(context), "");
        }
        @Override public UncertaintyAssessment assessUncertainty(CognitiveCycleOperations.CycleContext context,
                String requestIdempotencyKey, Instant at) {
            return uncertainty.assess(context, requestIdempotencyKey, at);
        }
        @Override public UncertaintyAssessment currentUncertaintyAssessment(CognitiveCycleOperations.CycleContext context, Instant at) {
            return uncertainty.current(context, at);
        }
        @Override public MetaControlInputs metaControlInputs(CognitiveCycleOperations.CycleContext context, Instant at) {
            return new MetaControlInputs(requireBudgetGuard().snapshot(context.executionId(), at),
                    currentUncertaintyAssessment(context, at), new ResourceUsage(Map.of()));
        }
        @Override public void recordMetaControlDecision(CognitiveCycleOperations.CycleContext context,
                MetaControlInputs inputs, dev.supplychain.agent.mechanisms.b6.MetaControlDecision decision, Instant at) {
            metaControlDecisions.put(context.executionId(), decision);
        }
        @Override public PlanningRequest revalidationRequest(CognitiveCycleOperations.CycleContext context,
                CognitiveCycleDecisionServices.SelectedPlanningCandidate selected,
                dev.supplychain.agent.memory.working.WorkingMemorySnapshot latestWorkingMemory, Instant at) {
            DurableCognitiveApplicationHost currentHost = requireHost();
            ActionDescriptor reserve = currentHost.actionRegistry().findHandler(SupplyChainGroundingActions.ReserveInventory.ID)
                    .orElseThrow().descriptor();
            PlanningRequest original = selected.request();
            return new PlanningRequest(context.executionId(), original.objective(), world.currentSnapshot(),
                    latestWorkingMemory, original.evidence(), List.of(reserve), original.objective().hardConstraints(),
                    original.scoringProfile(), original.riskPolicy(), original.authorityPolicy(), original.complexity(),
                    original.budget(), at, original.resumeSessionId(), context.requireUncertaintyAssessmentReference(),
                    original.plannerSelectionOverride(), original.reusableRecoveryProcedures(),
                    dev.supplychain.agent.mechanisms.b6.UncertaintyGovernanceDirective.from(
                            currentUncertaintyAssessment(context, at)), null);
        }
        @Override public List<ActionDispatchCommand<?, ?>> actionCommands(CognitiveCycleOperations.CycleContext context,
                CognitiveCycleDecisionServices.SelectedPlanningCandidate selected, Instant at) {
            List<ActionDispatchCommand<?, ?>> commands = new ArrayList<>();
            for (PlanningAction action : selected.candidate().actions()) {
                if (!(action.simulationCommand() instanceof SimulationCommand.ReserveInventory reserve)) {
                    throw new SecurityException("GAP-08 acceptance only permits the fixture's typed inventory reservation command");
                }
                var input = new ReserveInventoryRequest(world.currentSnapshot().version(), reserve.reservationId(),
                        reserve.orderId(), reserve.orderLineId(), reserve.lotId(), reserve.fulfillmentQuantity());
                commands.add(new ActionDispatchCommand<>(action.planningActionId(), action.descriptorId(),
                        ReserveInventoryRequest.class, SimulationResult.class, input));
            }
            return List.copyOf(commands);
        }
        @Override public CognitiveCycleOperations.ExternalObservation observeAndReconcile(CognitiveCycleOperations.CycleContext context,
                ActionExecutionBatch batch, Instant at) {
            if (context.executionId().equals(FIRST_EXECUTION)
                    && batch.status() == ActionExecutionBatch.BatchStatus.UNKNOWN_OUTCOME) {
                uncertainFirstBatch.compareAndSet(null, batch);
                if (interruptBeforeUnknownOutcomeReconciliation.compareAndSet(true, false)) {
                    throw new IllegalStateException("GAP-08 simulated host interruption after the external effect and before reconciliation");
                }
            }
            boolean successfulReceipts = batch.status() == ActionExecutionBatch.BatchStatus.SUCCEEDED
                    && !batch.items().isEmpty()
                    && batch.items().stream().allMatch(item -> item.status() == dev.supplychain.agent.actions.api.ActionStatus.SUCCEEDED
                            || item.status() == dev.supplychain.agent.actions.api.ActionStatus.ALREADY_COMPLETED);
            boolean reconciliableUnknown = batch.status() == ActionExecutionBatch.BatchStatus.UNKNOWN_OUTCOME
                    && batch.items().size() == 1
                    && batch.items().getFirst().actionId().equals(SupplyChainGroundingActions.ReserveInventory.ID.value())
                    && batch.items().getFirst().status() == dev.supplychain.agent.actions.api.ActionStatus.FAILED
                    && batch.items().getFirst().outcomeUnknown();
            if (!successfulReceipts && !reconciliableUnknown) {
                return new CognitiveCycleOperations.ExternalObservation(CognitiveCycleOperations.ObservationDisposition.NOT_VERIFIED,
                        "gap08-observation:" + context.executionId(), "gap08-post-action-evidence:" + context.executionId(),
                        "the durable action batch has neither successful receipts nor the narrowly recognized unknown reservation outcome");
            }
            RecoveryObjective objective = fixture(context).planningRequest().objective();
            boolean satisfied = objective.affectedOrders().stream().allMatch(orderId -> {
                CustomerOrder order = world.currentSnapshot().customerOrder(orderId);
                return order.lines().values().stream().allMatch(line -> line.fulfilledQuantity()
                        .plus(world.currentSnapshot().reservedFulfillmentQuantity(orderId, line.id()))
                        .plus(world.currentSnapshot().committedShipmentFulfillmentQuantity(orderId, line.id()))
                        .compareTo(line.requestedQuantity()) >= 0);
            });
            return new CognitiveCycleOperations.ExternalObservation(satisfied ? CognitiveCycleOperations.ObservationDisposition.OBJECTIVE_SATISFIED
                    : CognitiveCycleOperations.ObservationDisposition.NOT_VERIFIED,
                    "gap08-observation:" + context.executionId(), "gap08-post-action-evidence:" + context.executionId(),
                    satisfied ? (reconciliableUnknown
                            ? "the authoritative simulator snapshot proves that the single unknown-outcome reservation took effect; no redispatch is permitted"
                            : "the authoritative simulator snapshot satisfies every protected order line and the durable batch contains successful receipts")
                            : "the simulator snapshot does not yet satisfy every protected order line");
        }
        @Override public EpisodeDraft outcomeEpisode(CognitiveCycleOperations.CycleContext context,
                CognitiveCycleOperations.ExternalObservation observation, Instant at) {
            return new EpisodeDraft("Durable recovery " + context.requestId(),
                    "The durable application reconciled the approved inventory reservation against the authoritative simulator snapshot.",
                    EpisodeOutcome.SUCCESS, at.minusSeconds(30), at, List.of(), List.of(),
                    List.of(new EvidenceReference("observation", observation.observationId())),
                    new Provenance(ProvenanceSource.SIMULATOR, "gap08-durable-simulator-verifier", at, at, "gap08-e2e-v1"));
        }
        @Override public ObjectiveSatisfactionProof objectiveSatisfactionProof(CognitiveCycleOperations.CycleContext context,
                CognitiveCycleOperations.ExternalObservation observation, Instant at) {
            return new ObjectiveSatisfactionProof(objectiveFingerprint(context),
                    List.of(new EvidenceReference("observation", observation.observationId())),
                    "gap08-durable-objective-verifier", "gap08-e2e-proof-v1", at);
        }
        @Override public void recordEscalation(CognitiveCycleOperations.CycleContext context, String reason, Instant at) {
            escalations.incrementAndGet();
        }
    }

    private static PlanningRequest copyRequest(PlanningRequest base, String executionId, RecoveryObjective objective,
            SupplyChainSnapshot currentWorld, dev.supplychain.agent.memory.working.WorkingMemorySnapshot working,
            List<ActionDescriptor> actionDefinitions, List<EvidenceReference> evidence,
            UncertaintyAssessment.Reference uncertainty, String resumeSessionId, Instant requestedAt) {
        return new PlanningRequest(executionId, objective, currentWorld, working, evidence, actionDefinitions,
                objective.hardConstraints(), base.scoringProfile(), base.riskPolicy(), base.authorityPolicy(),
                base.complexity(), base.budget(), requestedAt, resumeSessionId, uncertainty);
    }

    private static final class ApprovalAuthority implements CognitiveCycleAuthorityPort {
        private final Map<String, ApprovalRecord> approvals = new ConcurrentHashMap<>();
        private final FixtureDirectory fixtures;
        private final AtomicInteger sequence = new AtomicInteger();
        private ApprovalAuthority(FixtureDirectory fixtures) {
            this.fixtures = java.util.Objects.requireNonNull(fixtures, "fixtures");
        }
        @Override public CognitiveCycleOperations.AuthorizationPrepared requestApproval(
                CognitiveCycleOperations.CycleContext context, ApprovalBinding binding,
                List<ActionDescriptor> actionDescriptors, Instant at) {
            if (actionDescriptors.size() != 1
                    || !actionDescriptors.getFirst().id().equals(SupplyChainGroundingActions.ReserveInventory.ID)
                    || actionDescriptors.getFirst().effect() != dev.supplychain.agent.actions.api.ActionEffect.DOMAIN_WRITE
                    || actionDescriptors.getFirst().requiredCapability() != Capability.INVENTORY_RESERVE) {
                throw new SecurityException("GAP-08 authority only approves the exact registered, capability-bound inventory reservation action");
            }
            String id = "gap08-approval:" + context.executionId() + ":" + sequence.incrementAndGet();
            approvals.put(id, new ApprovalRecord(binding));
            return new CognitiveCycleOperations.AuthorizationPrepared(
                    CognitiveCycleOperations.AuthorizationDisposition.REQUIRES_APPROVAL, true, id,
                    "external test authority requires authenticated approval for this exact action-plan binding");
        }
        void approve(String approvalId, String responseId, String approverId) {
            approvals.compute(approvalId, (id, current) -> {
                if (current == null) throw new IllegalArgumentException("unknown GAP-08 approval: " + id);
                return new ApprovalRecord(current.binding(), responseId, approverId);
            });
        }
        boolean isPending(String approvalId) {
            ApprovalRecord record = approvals.get(approvalId);
            return record != null && !record.approved();
        }
        @Override public CognitiveCycleOperations.ApprovalResolved resolveApproval(
                CognitiveCycleOperations.CycleContext context, ApprovalBinding binding, Instant at) {
            ApprovalRecord record = approvals.get(context.approvalId());
            if (record == null || !record.binding().equals(binding) || !record.approved()
                    || !record.responseId().equals(context.approvalResponseId())
                    || !record.approverId().equals(context.approverId())) {
                throw new SecurityException("GAP-08 external approval does not authenticate the exact durable plan binding");
            }
            return new CognitiveCycleOperations.ApprovalResolved(CognitiveCycleOperations.ApprovalDisposition.APPROVED,
                    context.approvalId(), binding.candidateId(), binding.candidateSetId(), binding.intentionId(),
                    binding.intentionVersion(), record.approverId(), "verified-approval-evidence:" + record.responseId(),
                    "external authority verified the exact plan, intention version, response and authenticated approver");
        }
        @Override public boolean isApprovalStillValid(CognitiveCycleOperations.CycleContext context,
                ApprovalBinding binding, String approvalId, Instant at) {
            ApprovalRecord record = approvals.get(approvalId);
            return record != null && record.approved() && record.binding().equals(binding)
                    && record.responseId().equals(context.approvalResponseId())
                    && record.approverId().equals(context.approverId());
        }
        @Override public HumanControlResolution resolveHumanControl(CognitiveCycleOperations.CycleContext context, Instant at) {
            return new HumanControlResolution(CognitiveCycleOperations.HumanControlAction.CONTINUE,
                    context.humanControlActorId().isBlank() ? "gap08-control-operator" : context.humanControlActorId(),
                    context.humanControlResponseId().isBlank() ? "gap08-control-response" : context.humanControlResponseId(),
                    "test authority continues the bounded deterministic cycle");
        }
        @Override public boolean isTerminationAuthorized(CognitiveCycleOperations.CycleContext context, String actorId, Instant at) {
            return false;
        }
        @Override public ActionPrincipal executionPrincipal(CognitiveCycleOperations.CycleContext context, Instant at) {
            ScenarioFixture fixture = fixtures.require(context.executionId());
            Set<ResourceReference> permittedResources = new HashSet<>();
            fixture.planningRequest().objective().affectedOrders().forEach(id ->
                    permittedResources.add(new ResourceReference("customer-order", id.value())));
            fixture.planningRequest().initialWorld().inventoryLots().keySet().forEach(id ->
                    permittedResources.add(new ResourceReference("inventory-lot", id.value())));
            if (permittedResources.isEmpty()) {
                throw new SecurityException("GAP-08 approval authority has no resources for this execution");
            }
            return new ActionPrincipal("gap08-authenticated-operator",
                    Set.of(CapabilityGrant.scoped(Capability.INVENTORY_RESERVE, Set.copyOf(permittedResources))));
        }
    }

    private record ApprovalRecord(ApprovalBinding binding, String responseId, String approverId) {
        private ApprovalRecord(ApprovalBinding binding) { this(binding, "", ""); }
        private ApprovalRecord {
            java.util.Objects.requireNonNull(binding, "binding");
            responseId = responseId == null ? "" : responseId;
            approverId = approverId == null ? "" : approverId;
        }
        boolean approved() { return !responseId.isBlank() && !approverId.isBlank(); }
    }
}
