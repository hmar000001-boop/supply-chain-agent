package dev.supplychain.agent.evaluation;

import dev.supplychain.agent.actions.api.ActionContext;
import dev.supplychain.agent.actions.api.ActionDescriptor;
import dev.supplychain.agent.actions.api.ActionHandler;
import dev.supplychain.agent.actions.api.ActionId;
import dev.supplychain.agent.actions.api.ActionPrincipal;
import dev.supplychain.agent.actions.api.PreconditionCheck;
import dev.supplychain.agent.actions.api.ActionRegistry;
import dev.supplychain.agent.actions.internal.ReasoningAction;
import dev.supplychain.agent.actions.internal.ReasoningAssessment;
import dev.supplychain.agent.actions.internal.ReasoningConfidence;
import dev.supplychain.agent.actions.internal.ReasoningProvider;
import dev.supplychain.agent.actions.api.ActionReceipt;
import dev.supplychain.agent.actions.api.ActionFailure;
import dev.supplychain.agent.actions.api.Capability;
import dev.supplychain.agent.actions.api.CapabilityGrant;
import dev.supplychain.agent.actions.api.ResourceReference;
import dev.supplychain.agent.core.interpretation.InterpretationOutcome;
import dev.supplychain.agent.core.interpretation.RequestInterpretation;
import dev.supplychain.agent.domain.common.Quantity;
import dev.supplychain.agent.domain.inventory.InventoryLot;
import dev.supplychain.agent.domain.inventory.LotQualityStatus;
import dev.supplychain.agent.domain.recovery.RecoveryObjective;
import dev.supplychain.agent.evaluation.catalog.ScenarioCatalog;
import dev.supplychain.agent.evaluation.fixture.ScenarioFixture;
import dev.supplychain.agent.evaluation.fixture.ScenarioFixtureFactory;
import dev.supplychain.agent.evaluation.host.FullGraphEvaluationHost;
import dev.supplychain.agent.infrastructure.postgres.checkpoint.PostgresLangGraph4jCheckpointSaverFactory;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresDataSourceFactory;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresDatabaseConfig;
import dev.supplychain.agent.infrastructure.postgres.migration.PostgresMigrationRunner;
import dev.supplychain.agent.infrastructure.postgres.runtime.JsonCodec;
import dev.supplychain.agent.infrastructure.postgres.runtime.PostgresRuntimeCodecs;
import dev.supplychain.agent.infrastructure.postgres.runtime.ObservationProvenanceResolver;
import dev.supplychain.agent.infrastructure.postgres.runtime.PostgresRuntimePersistence;
import dev.supplychain.agent.evaluation.planner.EvaluationPlannerFactory;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.Provenance;
import dev.supplychain.agent.memory.common.ProvenanceSource;
import dev.supplychain.agent.memory.episodic.EpisodeOutcome;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalQuery;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalResult;
import dev.supplychain.agent.memory.working.MemoryValue;
import dev.supplychain.agent.memory.working.WorkingMemoryId;
import dev.supplychain.agent.memory.working.WorkingMemorySnapshot;
import dev.supplychain.agent.mechanisms.b1.B1CandidateFeatureProvider;
import dev.supplychain.agent.mechanisms.b1.B1ScoringPolicy;
import dev.supplychain.agent.mechanisms.b1.B1SelectionRequest;
import dev.supplychain.agent.mechanisms.b1.B1SelectionResult;
import dev.supplychain.agent.mechanisms.b3.ConsumerReceiptStatus;
import dev.supplychain.agent.mechanisms.b3.WorkspaceAdmissionRequest;
import dev.supplychain.agent.mechanisms.b3.WorkspaceBroadcastReport;
import dev.supplychain.agent.mechanisms.b3.WorkspaceCandidate;
import dev.supplychain.agent.mechanisms.b3.WorkspaceCandidateKind;
import dev.supplychain.agent.mechanisms.b3.WorkspaceConsumer;
import dev.supplychain.agent.mechanisms.b3.WorkspaceConsumerReceipt;
import dev.supplychain.agent.mechanisms.b3.WorkspaceScoringPolicy;
import dev.supplychain.agent.mechanisms.b2.ImpasseSeverity;
import dev.supplychain.agent.mechanisms.b2.ImpasseSignal;
import dev.supplychain.agent.mechanisms.b2.ImpasseType;
import dev.supplychain.agent.mechanisms.b2.ResolutionDisposition;
import dev.supplychain.agent.mechanisms.b4.DelegationReturn;
import dev.supplychain.agent.mechanisms.b4.ObjectiveSatisfactionProof;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntentionId;
import dev.supplychain.agent.mechanisms.b4.RecoveryMethodReference;
import dev.supplychain.agent.mechanisms.b4.ObjectiveFingerprint;
import dev.supplychain.agent.mechanisms.b6.MetaControlDecision;
import dev.supplychain.agent.mechanisms.b6.ResourceBudget;
import dev.supplychain.agent.mechanisms.b6.ResourceUnit;
import dev.supplychain.agent.mechanisms.b6.ResourceBudgetSnapshot;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment;
import dev.supplychain.agent.mechanisms.b6.UncertaintyDimension;
import dev.supplychain.agent.mechanisms.b6.UncertaintySignal;
import dev.supplychain.agent.planning.api.ActionImpactEstimate;
import dev.supplychain.agent.planning.api.CandidateEvaluator;
import dev.supplychain.agent.planning.api.DeterministicCandidateEvaluator;
import dev.supplychain.agent.planning.api.PlanningAction;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningWorldModel;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;
import dev.supplychain.agent.runtime.cognitive.ResourceBudgetExecutionGuard;
import dev.supplychain.agent.runtime.langchain4j.BudgetedReasoningProvider;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleState;
import dev.supplychain.agent.runtime.cognitive.decision.CognitiveCycleDecisionPort;
import dev.supplychain.agent.runtime.cognitive.decision.CognitiveCycleDecisionServices;
import dev.supplychain.agent.runtime.cognitive.lifecycle.ActionDispatchCommand;
import dev.supplychain.agent.runtime.cognitive.lifecycle.ActionExecutionBatch;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleAuthorityPort;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleAuthorityPort.ApprovalBinding;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort.ChildInvestigationPlan;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort.ChildInvestigationStep;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort.ContextFact;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort.EpisodeDraft;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort.ExternalEvidenceDecision;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort.IntentionSeed;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort.MetaControlInputs;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort.IntentionDirective;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort.ImpasseOpenRequest;
import dev.supplychain.agent.simulator.command.SimulationCommand;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M8-B4-B exercises the real application composition and full cognitive-cycle graph. Scenario
 * adapters are deterministic, in-memory and explicitly limited to the disposable test process.
 */
class M8B4FullGraphWorkflowTest {

    @Test
    @EnabledIfSystemProperty(named = "phase0.postgres", matches = "true")
    void completeApplicationGraphResumesFromPostgresCheckpointInFreshJvmAfterAbruptDeath() throws Exception {
        String scenarioId = "FAIL-11";
        ScenarioFixture fixture = new ScenarioFixtureFactory(ScenarioCatalog.loadDefault()).create(scenarioId);
        String scopeId = fixture.planningRequest().workingMemory().scopeId().value();
        String executionId = fixture.planningRequest().executionId();
        String threadId = PostgresLangGraph4jCheckpointSaverFactory.threadIdForExecution(scopeId, executionId);

        runFullGraphChild("pause", scenarioId, threadId);
        runFullGraphChild("resume", scenarioId, threadId);
    }

    private static void runFullGraphChild(String mode, String scenarioId, String threadId) throws Exception {
        String classpath = System.getProperty("surefire.test.class.path");
        if (classpath == null || classpath.isBlank()) classpath = System.getProperty("java.class.path");
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Path log = Files.createTempFile("r1def-full-graph-" + mode + "-", ".log");
        Process process = new ProcessBuilder(javaExecutable, "-cp", classpath,
                R1DefFullGraphForkedProbe.class.getName(), mode, scenarioId, threadId)
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(90, TimeUnit.SECONDS), "forked full-graph process did not terminate: " + mode);
            String output = Files.readString(log, StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), "forked full-graph process failed (" + mode + "):\n" + output);
            assertTrue(output.contains("R1DEF_FULL_GRAPH_" + mode.toUpperCase(java.util.Locale.ROOT)), output);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(log);
        }
    }

    /** Child JVM deliberately halted after checkpointing the full graph at the clarification boundary. */
    public static final class R1DefFullGraphForkedProbe {
        private R1DefFullGraphForkedProbe() { }

        public static void main(String[] args) throws Exception {
            if (args.length != 3) throw new IllegalArgumentException("expected mode,scenarioId,threadId");
            String mode = args[0];
            String scenarioId = args[1];
            String threadId = args[2];
            var dataSource = PostgresDataSourceFactory.create(PostgresDatabaseConfig.fromEnvironment(System.getenv()));
            new PostgresMigrationRunner(dataSource).migrate();
            ScenarioRuntime runtime = ScenarioRuntime.createForPostgres(scenarioId,
                    "pause".equals(mode), threadId, dataSource);
            if ("pause".equals(mode)) {
                runtime.pauseBeforeClarification();
                System.out.println("R1DEF_FULL_GRAPH_PAUSED thread=" + threadId);
                System.out.flush();
                Runtime.getRuntime().halt(0);
            }
            if ("resume".equals(mode)) {
                TerminalOutput terminal = runtime.resumeClarificationAfterRestart();
                assertEquals(END, terminal.node());
                assertEquals("ESCALATED", terminal.state().text(CognitiveCycleState.DECISION_STATUS));
                runtime.assertDurablePlannerReservationCommitted();
                System.out.println("R1DEF_FULL_GRAPH_RESUMED thread=" + threadId
                        + " status=" + terminal.state().text(CognitiveCycleState.DECISION_STATUS));
                return;
            }
            throw new IllegalArgumentException("unknown full-graph probe mode: " + mode);
        }
    }

    @Test
    void authorizedWriteCompletesTheFullGraphOnlyAfterExactApprovalAndObservation() throws Exception {
        var scenario = ScenarioRuntime.create("REQ-09", ApprovalMode.APPROVED);
        var first = scenario.runUntilApproval();
        assertTrue(first, "the full application graph must stop before its approval node");
        assertEquals(0, scenario.effects.get(), "no M3 action may execute before host approval");
        var paused = scenario.graph.getState(scenario.config).state();
        assertFalse(paused.text(CognitiveCycleState.CANDIDATE_SET_ID).isBlank());
        assertFalse(paused.text(CognitiveCycleState.SELECTED_CANDIDATE_ID).isBlank());
        ApprovalBinding issuedBinding = scenario.authority.currentBinding;
        assertEquals(paused.text(CognitiveCycleState.REQUEST_ID), issuedBinding.requestId());
        assertEquals(paused.text(CognitiveCycleState.EXECUTION_ID), issuedBinding.executionId());
        assertEquals(paused.text(CognitiveCycleState.MEMORY_SCOPE_ID), issuedBinding.memoryScopeId());
        assertEquals(paused.text(CognitiveCycleState.CANDIDATE_SET_ID), issuedBinding.candidateSetId());
        assertEquals(paused.text(CognitiveCycleState.SELECTED_CANDIDATE_ID), issuedBinding.candidateId());
        assertEquals(paused.text(CognitiveCycleState.INTENTION_ID), issuedBinding.intentionId());
        assertEquals(paused.number(CognitiveCycleState.INTENTION_VERSION), issuedBinding.intentionVersion());
        assertFalse(issuedBinding.actionPlanFingerprint().isBlank());

        var terminal = scenario.resumeApproval("approval-response:REQ-09", "authorized-operator");
        assertEquals(END, terminal.node());
        assertEquals("COMPLETED", terminal.state().text(CognitiveCycleState.DECISION_STATUS));
        assertEquals("APPROVED", terminal.state().text(CognitiveCycleState.APPROVAL_DECISION));
        assertEquals("OBJECTIVE_SATISFIED", terminal.state().text(CognitiveCycleState.OBSERVATION_DISPOSITION));
        assertTrue(scenario.effects.get() > 0, "the approved plan must dispatch at least one registered action");
        assertEquals(scenario.effects.get(), scenario.lifecycle.observationsReconciled.get());
        assertTrue(terminal.state().events().stream().anyMatch(event -> event.startsWith("outcome-episode-recorded-unverified:")));
        assertEquals(0, scenario.lifecycle.escalations.get());
    }

    @Test
    void supplierDelayCompletesTheFullGraphAsAProposalWithoutDispatch() throws Exception {
        var scenario = ScenarioRuntime.create("REQ-01", ApprovalMode.DENIED);
        assertTrue(scenario.runUntilApproval());
        assertTrue(scenario.decision.lastPlanningRequest.evidence().contains(
                new EvidenceReference("supplier-status", "supplier:REQ-01:v0")),
                "the supplier disruption evidence must remain attached to the planning request");
        assertEquals(0, scenario.effects.get(), "the graph must not execute while the proposal is awaiting authority");

        var terminal = scenario.resumeApproval("supplier-delay-declined", "operator-declined");
        assertEquals(END, terminal.node());
        assertEquals("ESCALATED", terminal.state().text(CognitiveCycleState.DECISION_STATUS));
        assertEquals("DENIED", terminal.state().text(CognitiveCycleState.APPROVAL_DECISION));
        assertEquals(0, scenario.effects.get());
        assertFalse(scenario.effectStore.objectiveSatisfied());
    }

    @Test
    void qualityHeldInventoryIsNeverDispatchedAndPartialFulfillmentIsNotReportedAsComplete() throws Exception {
        var scenario = ScenarioRuntime.create("REQ-05", ApprovalMode.APPROVED);
        assertTrue(scenario.runUntilApproval());

        var terminal = scenario.resumeApproval("quality-hold-reviewed", "authorized-operator");
        assertEquals(END, terminal.node());
        assertEquals("ESCALATED", terminal.state().text(CognitiveCycleState.DECISION_STATUS));
        assertEquals("NOT_VERIFIED", terminal.state().text(CognitiveCycleState.OBSERVATION_DISPOSITION));
        assertTrue(scenario.effects.get() > 0, "the feasible qualified stock may be reserved under approval");
        assertFalse(scenario.effectStore.executedLotIds().contains("LOT-A-REQ_05"),
                "the primary lot is on quality hold and must never reach the action handler");
        assertFalse(scenario.effectStore.objectiveSatisfied(),
                "15 available qualified units cannot satisfy the 20-unit recovery objective");
        assertEquals(0, scenario.lifecycle.outcomeEpisodes.get(),
                "an unfulfilled objective must not be recorded as a successful outcome episode");
    }

    @Test
    void blockedRouteEvidenceRemainsBoundToTheProposalAndDoesNotDispatch() throws Exception {
        var scenario = ScenarioRuntime.create("REQ-04", ApprovalMode.DENIED);
        assertTrue(scenario.runUntilApproval());
        var evidence = scenario.decision.lastPlanningRequest.evidence();
        assertTrue(evidence.contains(new EvidenceReference("shipment-status", "shipment:REQ-04:v0")));
        assertTrue(evidence.contains(new EvidenceReference("route-status", "route:REQ-04:v0")));
        var terminal = scenario.resumeApproval("blocked-route-declined", "operator-declined");
        assertEquals(END, terminal.node());
        assertEquals("ESCALATED", terminal.state().text(CognitiveCycleState.DECISION_STATUS));
        assertEquals(0, scenario.effects.get(), "route-dependent proposal must not create an unapproved domain write");
    }

    @Test
    void conflictingInventoryEvidenceIsPreservedAsDistinctReferencesAndCannotDispatchByItself() throws Exception {
        var scenario = ScenarioRuntime.create("REQ-03", ApprovalMode.DENIED);
        assertTrue(scenario.runUntilApproval());
        var evidence = scenario.decision.lastPlanningRequest.evidence();
        assertTrue(evidence.contains(new EvidenceReference("inventory-observation", "observation:stale:REQ-03")));
        assertTrue(evidence.contains(new EvidenceReference("inventory-observation", "observation:current:REQ-03")));
        var terminal = scenario.resumeApproval("conflicting-inventory-declined", "operator-declined");
        assertEquals(END, terminal.node());
        assertEquals("ESCALATED", terminal.state().text(CognitiveCycleState.DECISION_STATUS));
        assertEquals(0, scenario.effects.get(), "conflicting evidence is not an authorization to write");
    }

    @Test
    void deniedAndRevokedApprovalsNeverReachTheActionRegistry() throws Exception {
        var denied = ScenarioRuntime.create("FAIL-07", ApprovalMode.DENIED);
        assertTrue(denied.runUntilApproval());
        var deniedTerminal = denied.resumeApproval("denied-response", "operator-denied");
        assertEquals("ESCALATED", deniedTerminal.state().text(CognitiveCycleState.DECISION_STATUS));
        assertEquals(0, denied.effects.get());

        var revoked = ScenarioRuntime.create("FAIL-08", ApprovalMode.REVOKED);
        assertTrue(revoked.runUntilApproval());
        var revokedTerminal = revoked.resumeApproval("revoked-response", "operator-approved-before-revocation");
        assertEquals("ESCALATED", revokedTerminal.state().text(CognitiveCycleState.DECISION_STATUS));
        assertEquals("APPROVED", revokedTerminal.state().text(CognitiveCycleState.APPROVAL_DECISION),
                "the human decision was approved, but the subsequent revocation check must block dispatch");
        assertTrue(revokedTerminal.state().text(CognitiveCycleState.LAST_REASON).contains("revoked"),
                "the final pre-dispatch validity check must explain why the previously approved action was blocked");
        assertEquals(0, revoked.effects.get(), "the approval is rechecked immediately before dispatch");
    }

    @Test
    void changedTrustedActionDefinitionRequiresFreshApprovalBeforeDispatch() throws Exception {
        var scenario = ScenarioRuntime.create("REQ-08", ApprovalMode.STALE_PLAN_THEN_DENY);
        assertTrue(scenario.runUntilApproval());
        String firstApprovalId = scenario.graph.getState(scenario.config).state().text(CognitiveCycleState.APPROVAL_ID);
        assertEquals(0, scenario.effects.get());
        var firstResume = scenario.resumeUntilNextApproval("first-approval-response", "operator-1");
        assertTrue(firstResume, "stale candidate must be discarded and a fresh candidate must request approval again");
        assertEquals(0, scenario.effects.get());
        assertEquals(2, scenario.authority.approvalRequests.get());
        String secondApprovalId = scenario.graph.getState(scenario.config).state().text(CognitiveCycleState.APPROVAL_ID);
        assertNotEquals(firstApprovalId, secondApprovalId, "a changed trusted plan must receive a new approval identity");
        assertTrue(scenario.decision.lastPlanningRequest.actionDefinitionsById().values().stream()
                .anyMatch(descriptor -> descriptor.name().contains("(refreshed)")),
                "the replanning round must use the refreshed trusted action definition");
        var terminal = scenario.resumeApproval("second-approval-declined", "operator-2");
        assertEquals(END, terminal.node());
        assertEquals("ESCALATED", terminal.state().text(CognitiveCycleState.DECISION_STATUS));
        assertEquals("DENIED", terminal.state().text(CognitiveCycleState.APPROVAL_DECISION));
        assertEquals(0, scenario.effects.get(), "the superseded approval must never authorize an effect");
    }

    @Test
    void demandChangeAfterApprovalInvalidatesTheOldPlanAndRequiresASecondApproval() throws Exception {
        var scenario = ScenarioRuntime.create("FAIL-04", ApprovalMode.DEMAND_CHANGED_THEN_DENY);
        assertTrue(scenario.runUntilApproval());
        assertEquals(0, scenario.effects.get());

        String firstApprovalId = scenario.graph.getState(scenario.config).state().text(CognitiveCycleState.APPROVAL_ID);
        boolean interruptedAgain = scenario.resumeUntilNextApproval("pre-change-approval", "operator-1");
        assertTrue(interruptedAgain, "changed demand must invalidate the old plan and request a new approval");
        assertEquals(1, scenario.worldStore.snapshot().version());
        assertEquals(1, scenario.decision.lastPlanningRequest.initialWorld().version(),
                "the replanning request must be built from the changed authoritative world");
        assertTrue(scenario.decision.lastPlanningRequest.evidence().contains(
                new EvidenceReference("demand-observation", "demand:post-approval:FAIL_04:v1")),
                "the changed demand must be accompanied by its new versioned evidence reference");
        assertEquals(2, scenario.authority.approvalRequests.get());
        assertNotEquals(firstApprovalId, scenario.graph.getState(scenario.config).state().text(CognitiveCycleState.APPROVAL_ID));
        assertEquals(0, scenario.effects.get(), "the pre-change approval must not authorize the changed demand plan");

        var terminal = scenario.resumeApproval("changed-demand-declined", "operator-2");
        assertEquals(END, terminal.node());
        assertEquals("ESCALATED", terminal.state().text(CognitiveCycleState.DECISION_STATUS));
        assertEquals("DENIED", terminal.state().text(CognitiveCycleState.APPROVAL_DECISION));
        assertEquals(0, scenario.effects.get());
    }

    @Test
    void newInventoryQualityObservationInvalidatesApprovedPlanAndForcesFullGraphReplanning() throws Exception {
        var scenario = ScenarioRuntime.create("REQ-08", ApprovalMode.OBSERVATION_INVALIDATES_PLAN);
        assertTrue(scenario.runUntilApproval());
        String originalApproval = scenario.graph.getState(scenario.config).state().text(CognitiveCycleState.APPROVAL_ID);
        assertFalse(originalApproval.isBlank());

        var terminal = scenario.resumeApproval("inventory-quality-alert", "authorized-operator");
        assertEquals(END, terminal.node());
        assertEquals("ESCALATED", terminal.state().text(CognitiveCycleState.DECISION_STATUS));
        assertEquals(0, scenario.effects.get(),
                "the old approval must not authorize inventory actions after the fresh snapshot invalidates every candidate");
        assertEquals(1, scenario.lifecycle.revalidationRequests.get());
        assertEquals(1, scenario.worldStore.snapshot().version());
        assertTrue(scenario.worldStore.snapshot().inventoryLots().values().stream()
                .allMatch(lot -> lot.qualityStatus() == LotQualityStatus.QUALITY_HOLD),
                "the second planning round must use the current authoritative quality-held inventory snapshot");
        assertTrue(scenario.decision.lastPlanningRequest.evidence().contains(
                new EvidenceReference("quality-alert", "quality:post-approval:REQ_08:v1")),
                "the replan must retain the new quality observation as versioned evidence");
        assertEquals(1, scenario.authority.approvalRequests.get(),
                "the stale approval must not be silently reused or converted into a new approval");
        assertEquals(0, scenario.lifecycle.outcomeEpisodes.get());
    }

    @Test
    void readOnlyAndInfeasibleFixturesFailClosedWithoutAWrite() throws Exception {
        var readOnly = ScenarioRuntime.create("REQ-10", ApprovalMode.APPROVED);
        var readOnlyResult = readOnly.runToTerminal();
        assertEquals(END, readOnlyResult.node());
        assertEquals("ESCALATED", readOnlyResult.state().text(CognitiveCycleState.DECISION_STATUS),
                "the current graph has no typed read-only assessment terminal; it must stop rather than invent a proposal");
        assertEquals(0, readOnly.effects.get());
        assertEquals(0, readOnly.authority.approvalRequests.get());

        var infeasible = ScenarioRuntime.create("FAIL-11", ApprovalMode.APPROVED);
        var infeasibleResult = infeasible.runToTerminal();
        assertEquals(END, infeasibleResult.node());
        assertEquals("ESCALATED", infeasibleResult.state().text(CognitiveCycleState.DECISION_STATUS));
        assertEquals(0, infeasible.effects.get());
        assertEquals(0, infeasible.authority.approvalRequests.get());
    }

    private enum ApprovalMode {
        APPROVED, DENIED, REVOKED, STALE_PLAN_THEN_DENY,
        DEMAND_CHANGED_THEN_DENY, OBSERVATION_INVALIDATES_PLAN
    }

    private record TerminalOutput(String node, CognitiveCycleState state) { }

    private static final class ScenarioRuntime {
        private final ScenarioFixture fixture;
        private final AtomicInteger effects;
        private final ScenarioWorldStore worldStore;
        private final ScenarioEffectStore effectStore;
        private final FullGraphEvaluationHost host = new FullGraphEvaluationHost();
        private final DecisionAdapter decision;
        private final LifecycleAdapter lifecycle;
        private final AuthorityAdapter authority;
        private final CompiledGraph<CognitiveCycleState> graph;
        private final RunnableConfig config;
        private final javax.sql.DataSource checkpointDataSource;

        private ScenarioRuntime(ScenarioFixture fixture, ApprovalMode approvalMode) throws Exception {
            this(fixture, approvalMode, false, null, null, null, null);
        }

        private ScenarioRuntime(ScenarioFixture fixture, ApprovalMode approvalMode, boolean clarifyOnFirstInterpretation,
                                String stableThreadId, javax.sql.DataSource checkpointDataSource,
                                ResourceBudgetExecutionGuard durableBudgetGuard,
                                ResourceBudget durableBudget) throws Exception {
            this.fixture = fixture;
            this.checkpointDataSource = checkpointDataSource;
            this.worldStore = new ScenarioWorldStore(fixture.planningRequest().initialWorld(), fixture.planningRequest().evidence());
            this.worldStore.initializeActionDescriptors(fixture.planningRequest().availableActionDefinitions());
            this.effectStore = new ScenarioEffectStore(fixture, worldStore);
            this.effects = effectStore.effectCount;
            Clock clock = Clock.fixed(ScenarioFixtureFactory.FIXTURE_AS_OF, ZoneOffset.UTC);
            var world = new ScenarioFixtureFactory(ScenarioCatalog.loadDefault());
            PlanningWorldModel worldModel = world.worldModel();
            DeterministicCandidateEvaluator evaluator = world.evaluator(worldModel);
            decision = new DecisionAdapter(fixture, worldStore);
            var registry = new ActionRegistry();
            var actionHandlers = new HashMap<ActionId, ReservationActionHandler>();
            fixture.planningRequest().availableActionDefinitions().forEach(descriptor -> {
                var handler = new ReservationActionHandler(descriptor, effectStore);
                registry.register(handler);
                actionHandlers.put(descriptor.id(), handler);
            });
            if (durableBudgetGuard != null) {
                ReasoningProvider deterministicReasoner = request -> new ReasoningAssessment(
                        "Evidence-bounded test assessment", List.of("Only supplied evidence is considered"),
                        List.of(), List.of(), request.evidence(), List.of(), ReasoningConfidence.UNASSESSED,
                        "R1-DEF verifies that the registered M3 reasoning action is bound to the same durable B6 guard");
                registry.register(new ReasoningAction(new BudgetedReasoningProvider(
                        deterministicReasoner, durableBudgetGuard)));
            }
            lifecycle = new LifecycleAdapter(fixture, clock, approvalMode, effectStore, worldStore, actionHandlers,
                    durableBudget);
            authority = new AuthorityAdapter(approvalMode, fixture);
            var consumers = Map.<String, WorkspaceConsumer>of("planner", broadcast -> new WorkspaceConsumerReceipt(
                    "planner", broadcast.snapshot().id(), ConsumerReceiptStatus.ACCEPTED,
                    broadcast.snapshot().entries().stream().map(entry -> entry.candidate().id()).toList(),
                    broadcast.broadcastAt(), "deterministic planner consumer accepted the admitted workspace"));
            var strategies = new EvaluationPlannerFactory(world).strategiesForFullGraph(fixture);
            AtomicInteger interpretationCalls = new AtomicInteger();
            var interpreter = (dev.supplychain.agent.core.interpretation.RequestInterpreter) request -> {
                if (clarifyOnFirstInterpretation && interpretationCalls.getAndIncrement() == 0) {
                    return new RequestInterpretation(InterpretationOutcome.NEEDS_CLARIFICATION,
                            request.originalText(), "The affected customer order must be identified before recovery.");
                }
                return new RequestInterpretation(InterpretationOutcome.READY, request.originalText(),
                        "Deterministic M8-B4 scenario interpretation");
            };
            var ports = new FullGraphEvaluationHost.Ports(decision, lifecycle, authority, interpreter,
                    worldModel, evaluator, strategies, clock, registry, consumers, durableBudgetGuard);
            if (checkpointDataSource == null) {
                graph = host.compileFull(ports);
            } else {
                graph = host.compileFull(ports, builder ->
                        PostgresLangGraph4jCheckpointSaverFactory.create(checkpointDataSource, builder));
            }
            String selectedThreadId = stableThreadId == null
                    ? "m8-b4:" + fixture.definition().scenarioId() + ":" + UUID.randomUUID()
                    : stableThreadId;
            config = RunnableConfig.builder().threadId(selectedThreadId).build();
        }

        static ScenarioRuntime create(String scenarioId, ApprovalMode mode) throws Exception {
            var catalog = ScenarioCatalog.loadDefault();
            var factory = new ScenarioFixtureFactory(catalog);
            return new ScenarioRuntime(factory.create(scenarioId), mode);
        }

        static ScenarioRuntime createForPostgres(String scenarioId, boolean clarifyOnFirstInterpretation,
                                                 String stableThreadId, javax.sql.DataSource dataSource) throws Exception {
            var fixture = new ScenarioFixtureFactory(ScenarioCatalog.loadDefault()).create(scenarioId);
            String scopeId = fixture.planningRequest().workingMemory().scopeId().value();
            String expectedThread = PostgresLangGraph4jCheckpointSaverFactory.threadIdForExecution(
                    scopeId, fixture.planningRequest().executionId());
            if (!expectedThread.equals(stableThreadId)) {
                throw new SecurityException("full-graph checkpoint thread does not match fixture scope/execution identity");
            }
            Clock clock = Clock.fixed(ScenarioFixtureFactory.FIXTURE_AS_OF, ZoneOffset.UTC);
            var persistence = new PostgresRuntimePersistence(dataSource, scopeId, unusedRuntimeCodecs());
            var limits = new EnumMap<ResourceUnit, Long>(ResourceUnit.class);
            for (ResourceUnit unit : ResourceUnit.values()) limits.put(unit, 1_000_000L);
            var budget = new ResourceBudget(limits, ScenarioFixtureFactory.FIXTURE_AS_OF.plus(Duration.ofDays(90)));
            ResourceBudgetExecutionGuard guard = persistence.newResourceBudgetExecutionGuard(
                    ignored -> budget, clock, Duration.ofMinutes(2));
            return new ScenarioRuntime(fixture, ApprovalMode.APPROVED, clarifyOnFirstInterpretation,
                    stableThreadId, dataSource, guard, budget);
        }

        private static PostgresRuntimeCodecs unusedRuntimeCodecs() {
            return PostgresRuntimeCodecs.withDefaults(
                    unusedCodec(), unusedCodec(), unusedCodec(), unusedCodec(),
                    observation -> new ObservationProvenanceResolver.ObservationProvenance(
                            "unused-execution", "unused-principal", "unused-source", ScenarioFixtureFactory.FIXTURE_AS_OF),
                    unusedCodec(), unusedCodec(), unusedCodec(), unusedCodec(), unusedCodec());
        }

        private static <T> JsonCodec<T> unusedCodec() {
            return new JsonCodec<>() {
                @Override public String encode(T value) { throw new UnsupportedOperationException("test codec is not used in the clarification restart scenario"); }
                @Override public T decode(String json) { throw new UnsupportedOperationException("test codec is not used in the clarification restart scenario"); }
            };
        }

        void pauseBeforeClarification() {
            var stream = graph.stream(GraphInput.args(input()), config);
            for (var ignored : stream) { /* run the actual application graph to its clarification breakpoint */ }
            assertTrue(org.bsc.langgraph4j.GraphResult.from(stream).isInterruptionMetadata(),
                    "full application graph did not pause at the host clarification boundary");
            assertEquals("NEEDS_CLARIFICATION", graph.getState(config).state().text(CognitiveCycleState.DECISION_STATUS));
            assertEquals(0, effects.get(), "no external action may run before clarification and subsequent authorization");
        }

        TerminalOutput resumeClarificationAfterRestart() throws Exception {
            var checkpoint = graph.getState(config).state();
            assertEquals("NEEDS_CLARIFICATION", checkpoint.text(CognitiveCycleState.DECISION_STATUS),
                    "fresh JVM must restore the full graph checkpoint from PostgreSQL");
            var updated = graph.updateState(config, Map.of(
                    CognitiveCycleState.CLARIFICATION_CONTEXT,
                    "The disrupted supplier serves the customer order identified by the scenario."), null);
            var output = graph.invokeFinal(GraphInput.resume(), updated)
                    .orElseThrow(() -> new AssertionError("full graph did not complete after process restart"));
            return new TerminalOutput(output.node(), output.state());
        }

        void assertDurablePlannerReservationCommitted() throws Exception {
            if (checkpointDataSource == null) {
                throw new IllegalStateException("durable planner reservation assertion requires PostgreSQL-backed runtime");
            }
            String scopeId = fixture.planningRequest().workingMemory().scopeId().value();
            String executionId = fixture.planningRequest().executionId();
            try (var connection = checkpointDataSource.getConnection();
                 var statement = connection.prepareStatement("""
                         SELECT COUNT(*) FROM b6_resource_budget_reservation
                         WHERE scope_id=? AND execution_id=? AND owner_id IS NOT NULL
                           AND status='COMMITTED' AND started_at IS NOT NULL
                           AND recovery_outcome='NONE'
                         """)) {
                statement.setString(1, scopeId);
                statement.setString(2, executionId);
                try (var row = statement.executeQuery()) {
                    if (!row.next() || row.getLong(1) < 1L) {
                        throw new AssertionError("full application graph did not durably commit its M5 planner reservation");
                    }
                }
            }
        }

        boolean runUntilApproval() {
            var stream = graph.stream(GraphInput.args(input()), config);
            for (var ignored : stream) { /* consume the real graph up to its static approval breakpoint */ }
            boolean interrupted = org.bsc.langgraph4j.GraphResult.from(stream).isInterruptionMetadata();
            assertTrue(interrupted, "scenario did not reach the approval interruption: "
                    + graph.getState(config).state().text(CognitiveCycleState.DECISION_STATUS));
            assertFalse(graph.getState(config).state().text(CognitiveCycleState.APPROVAL_ID).isBlank(),
                    "the interrupted graph must retain the exact host-issued approval reference");
            return interrupted;
        }

        boolean resumeUntilNextApproval(String responseId, String approverId) throws Exception {
            var updated = graph.updateState(config, Map.of(
                    CognitiveCycleState.APPROVAL_RESPONSE_ID, responseId,
                    CognitiveCycleState.APPROVER_ID, approverId), null);
            var stream = graph.stream(GraphInput.resume(), updated);
            for (var ignored : stream) { /* a stale-plan branch must create a new approval boundary */ }
            return org.bsc.langgraph4j.GraphResult.from(stream).isInterruptionMetadata();
        }

        TerminalOutput resumeApproval(String responseId, String approverId) throws Exception {
            var updated = graph.updateState(config, Map.of(
                    CognitiveCycleState.APPROVAL_RESPONSE_ID, responseId,
                    CognitiveCycleState.APPROVER_ID, approverId), null);
            var output = graph.invokeFinal(GraphInput.resume(), updated)
                    .orElseThrow(() -> new AssertionError("resumed cognitive workflow returned no result"));
            return new TerminalOutput(output.node(), output.state());
        }

        TerminalOutput runToTerminal() {
            var output = graph.invokeFinal(GraphInput.args(input()), config)
                    .orElseThrow(() -> new AssertionError("full graph produced no terminal result"));
            return new TerminalOutput(output.node(), output.state());
        }

        private Map<String, Object> input() {
            String scenarioId = fixture.definition().scenarioId();
            return Map.of(
                    CognitiveCycleState.REQUEST_ID, "request:" + scenarioId,
                    CognitiveCycleState.EXECUTION_ID, fixture.planningRequest().executionId(),
                    CognitiveCycleState.MEMORY_SCOPE_ID, fixture.planningRequest().workingMemory().scopeId().value(),
                    CognitiveCycleState.REQUEST, "Assess and recover supply-chain disruption for " + scenarioId);
        }
    }

    private static UncertaintyAssessment uncertainty(CognitiveCycleOperations.CycleContext context, Instant at) {
        var reference = context.requireUncertaintyAssessmentReference();
        return new UncertaintyAssessment(reference, at.minusSeconds(1), at.plusSeconds(300),
                "m8-b4-scenario-uncertainty:" + context.executionId(),
                UncertaintyAssessment.Calibration.notCalibrated(), Map.of(
                UncertaintyDimension.EVIDENCE, new UncertaintySignal(UncertaintyDimension.EVIDENCE, 0.1, "fixture evidence", 1),
                UncertaintyDimension.HYPOTHESIS, new UncertaintySignal(UncertaintyDimension.HYPOTHESIS, 0.1, "fixture hypothesis", 1),
                UncertaintyDimension.PLAN, new UncertaintySignal(UncertaintyDimension.PLAN, 0.1, "fixture plan", 1)));
    }

    private static final class DecisionAdapter implements CognitiveCycleDecisionPort {
        private final ScenarioFixture fixture;
        private final ScenarioWorldStore worldStore;
        private WorkspaceBroadcastReport lastBroadcast;
        private PlanningRequest lastPlanningRequest;

        private DecisionAdapter(ScenarioFixture fixture, ScenarioWorldStore worldStore) {
            this.fixture = fixture;
            this.worldStore = worldStore;
        }

        @Override public MemoryScopeId memoryScope(CognitiveCycleOperations.CycleContext context) {
            return new MemoryScopeId(context.memoryScopeId());
        }

        @Override public MemoryRetrievalQuery retrievalQuery(CognitiveCycleOperations.CycleContext context, Instant at) {
            return new MemoryRetrievalQuery(memoryScope(context), null, null, null, at, at, 20);
        }

        @Override public B1SelectionRequest b1SelectionRequest(CognitiveCycleOperations.CycleContext context, Instant at) {
            return new B1SelectionRequest(memoryScope(context), Set.of("supplier", "inventory", "disruption"), at,
                    100, 20, Duration.ofDays(7), new B1ScoringPolicy(0.0, 0.0, 0.0, 0.0));
        }

        @Override public B1CandidateFeatureProvider b1FeatureProvider(CognitiveCycleOperations.CycleContext context) {
            return reference -> { throw new AssertionError("the scenario starts with no persisted long-term memories"); };
        }

        @Override public CognitiveCycleOperations.EvidenceRetrieved persistEvidenceSelection(
                CognitiveCycleOperations.CycleContext context, MemoryRetrievalResult retrieved,
                B1SelectionResult selected, Instant at) {
            return new CognitiveCycleOperations.EvidenceRetrieved(CognitiveCycleOperations.EvidenceDisposition.READY,
                    "evidence-set:" + context.requestId(), fixture.planningRequest().complexity().uncertainty(),
                    "versioned scenario evidence set persisted by the deterministic test adapter");
        }

        @Override public UncertaintyAssessment currentUncertaintyAssessment(CognitiveCycleOperations.CycleContext context, Instant at) {
            return uncertainty(context, at);
        }

        @Override public WorkspaceAdmissionRequest workspaceAdmissionRequest(
                CognitiveCycleOperations.CycleContext context, Instant at) {
            MemoryScopeId scope = memoryScope(context);
            return new WorkspaceAdmissionRequest("cycle:" + context.executionId(), scope, 10, 5,
                    Set.of("current-request"), Map.of("current-request", 1.0),
                    new WorkspaceScoringPolicy(0.0, 1.0),
                    List.of(new WorkspaceCandidate("request-context:" + context.requestId(), scope,
                            WorkspaceCandidateKind.OBSERVATION, 2.0, 1.0, 0.0, 2,
                            Set.of("current-request"), Set.of(), true,
                            List.of(new EvidenceReference("request", context.requestId())))), at,
                    context.requireUncertaintyAssessmentReference());
        }

        @Override public Set<String> workspaceConsumers(CognitiveCycleOperations.CycleContext context) {
            return Set.of("planner");
        }

        @Override public void recordWorkspaceBroadcast(CognitiveCycleOperations.CycleContext context,
                                                        WorkspaceBroadcastReport report, Instant at) {
            lastBroadcast = report;
            if (!report.allAccepted()) throw new IllegalStateException("scenario planner did not acknowledge the workspace");
        }

        @Override public PlanningRequest planningRequest(CognitiveCycleOperations.CycleContext context, Instant at) {
            var base = fixture.planningRequest();
            var scope = memoryScope(context);
            var workingMemoryId = new WorkingMemoryId("working-memory:" + context.executionId());
            Instant expiry = at.plus(Duration.ofDays(1));
            var working = WorkingMemorySnapshot.empty(workingMemoryId, scope, at, expiry)
                    .put("request:current", new MemoryValue.Text(context.request()),
                            List.of(new EvidenceReference("request", context.requestId())), at, expiry);
            lastPlanningRequest = copy(base, working, worldStore.snapshot(),
                    worldStore.actionDescriptors(base.availableActionDefinitions()),
                    worldStore.evidence(base.evidence()), context.executionId(),
                    context.requireUncertaintyAssessmentReference());
            return lastPlanningRequest;
        }

        private PlanningRequest copy(PlanningRequest base, WorkingMemorySnapshot working,
                                     dev.supplychain.agent.domain.world.SupplyChainSnapshot world,
                                     List<ActionDescriptor> descriptors, List<EvidenceReference> evidence, String executionId,
                                     UncertaintyAssessment.Reference uncertaintyReference) {
            return new PlanningRequest(executionId, base.objective(), world, working, evidence,
                    descriptors, base.hardConstraints(), base.scoringProfile(), base.riskPolicy(),
                    base.authorityPolicy(), base.complexity(), base.budget(), base.requestedAt(), base.resumeSessionId(),
                    uncertaintyReference);
        }
    }

    private static final class LifecycleAdapter implements CognitiveCycleLifecyclePort {
        private final ScenarioFixture fixture;
        private final Clock clock;
        private final ScenarioEffectStore effects;
        private final ScenarioWorldStore worldStore;
        private final Map<ActionId, ReservationActionHandler> actionHandlers;
        private final AtomicInteger observationsReconciled = new AtomicInteger();
        private final AtomicInteger escalations = new AtomicInteger();
        private final AtomicInteger outcomeEpisodes = new AtomicInteger();
        private final AtomicInteger revalidationRequests = new AtomicInteger();
        private final RecoveryObjective objective;
        private final ResourceBudget durableBudget;
        private final Map<String, UncertaintyAssessment.Reference> uncertaintyByTrigger = new java.util.HashMap<>();
        private final Map<UncertaintyAssessment.Reference, UncertaintyAssessment> uncertaintyByReference = new java.util.HashMap<>();

        private LifecycleAdapter(ScenarioFixture fixture, Clock clock, ApprovalMode approvalMode,
                                 ScenarioEffectStore effects, ScenarioWorldStore worldStore,
                                 Map<ActionId, ReservationActionHandler> actionHandlers, ResourceBudget durableBudget) {
            this.fixture = fixture;
            this.clock = clock;
            this.effects = effects;
            this.worldStore = worldStore;
            this.actionHandlers = Map.copyOf(actionHandlers);
            this.approvalMode = approvalMode;
            this.objective = fixture.planningRequest().objective();
            this.durableBudget = durableBudget;
        }

        private final ApprovalMode approvalMode;

        @Override public MemoryScopeId memoryScope(CognitiveCycleOperations.CycleContext context) {
            return new MemoryScopeId(context.memoryScopeId());
        }

        @Override public WorkingMemoryId workingMemoryId(CognitiveCycleOperations.CycleContext context) {
            return new WorkingMemoryId("working-memory:" + context.executionId());
        }

        @Override public dev.supplychain.agent.core.interpretation.InterpretationRequest interpretationRequest(
                CognitiveCycleOperations.CycleContext context, Instant at) {
            return new dev.supplychain.agent.core.interpretation.InterpretationRequest(
                    context.request(), context.clarificationContext(), context.clarificationRound());
        }

        @Override public void persistClarification(CognitiveCycleOperations.CycleContext context, Instant at) { }

        @Override public Optional<IntentionSeed> intentionSeed(CognitiveCycleOperations.CycleContext context, Instant at) {
            return Optional.of(new IntentionSeed(new RecoveryIntentionId("intention:" + context.executionId()),
                    objective, "m8-b4-test-owner", new RecoveryMethodReference("m8-b4-initial-method", "candidate:pending")));
        }

        @Override public List<ContextFact> contextFacts(CognitiveCycleOperations.CycleContext context,
                                                        WorkingMemorySnapshot current, Instant at) {
            return List.of(new ContextFact("request:current", new MemoryValue.Text(context.request()),
                    List.of(new EvidenceReference("request", context.requestId()))));
        }

        @Override public ExternalEvidenceDecision acceptExternalEvidence(CognitiveCycleOperations.CycleContext context, Instant at) {
            return new ExternalEvidenceDecision(CognitiveCycleOperations.EvidenceAcceptance.REJECTED, "", List.of(),
                    "the deterministic scenario already carries its versioned evidence set");
        }

        @Override public String objectiveFingerprint(CognitiveCycleOperations.CycleContext context) {
            return ObjectiveFingerprint.from(objective).value();
        }

        @Override public ImpasseOpenRequest impasseOpenRequest(CognitiveCycleOperations.CycleContext context, Instant at) {
            String reason = context.lastReason().isBlank() ? "No feasible candidate under the current scenario constraints" : context.lastReason();
            var signal = new ImpasseSignal(ImpasseType.NO_FEASIBLE_CANDIDATE, ImpasseSeverity.HIGH,
                    "candidate-set:" + context.candidateSetId(), reason,
                    List.of(new EvidenceReference("scenario-fixture", fixture.definition().scenarioId())), at);
            // The child is allowed to inspect inventory but receives no write capability.
            return new ImpasseOpenRequest("m8-b4-impasse:" + context.executionId(), signal,
                    Set.of(Capability.INVENTORY_READ), Set.of(Capability.INVENTORY_READ),
                    at.plusSeconds(60), 1);
        }

        @Override public ChildInvestigationPlan planChildInvestigation(CognitiveCycleOperations.CycleContext context,
                dev.supplychain.agent.mechanisms.b2.ImpasseResolutionCase resolutionCase, Instant at) {
            // These fixtures intentionally do not invent missing supply or evidence. The typed unresolved
            // result returns control to the parent intention, which the graph then escalates safely.
            return new ChildInvestigationPlan(List.of(), ResolutionDisposition.UNRESOLVED,
                    List.of(), DelegationReturn.NO_METHOD_CHANGE);
        }

        @Override public List<dev.supplychain.agent.mechanisms.b2.ResolutionFinding> executeChildInvestigationStep(
                CognitiveCycleOperations.CycleContext context,
                dev.supplychain.agent.mechanisms.b2.ImpasseResolutionCase resolutionCase,
                ChildInvestigationStep step, Instant at) {
            throw new UnsupportedOperationException("unexpected B2 child action in B4-B workflow fixture");
        }

        @Override public dev.supplychain.agent.mechanisms.b2.ParentContextSnapshot currentParentContext(
                CognitiveCycleOperations.CycleContext context, WorkingMemorySnapshot latestWorkingMemory) {
            return new dev.supplychain.agent.mechanisms.b2.ParentContextSnapshot(latestWorkingMemory.scopeId(),
                    context.requestId(), latestWorkingMemory.version(), objectiveFingerprint(context));
        }

        @Override public IntentionDirective intentionDirective(CognitiveCycleOperations.CycleContext context,
                dev.supplychain.agent.mechanisms.b4.RecoveryIntention current, Instant at) {
            return new IntentionDirective(CognitiveCycleOperations.IntentionAction.CONTINUE,
                    "preserve the active recovery objective during this workflow", null,
                    objectiveFingerprint(context), "");
        }

        @Override public UncertaintyAssessment assessUncertainty(CognitiveCycleOperations.CycleContext context,
                String triggerId, Instant at) {
            UncertaintyAssessment.Reference reference = uncertaintyByTrigger.computeIfAbsent(triggerId, ignored -> {
                var previous = context.uncertaintyAssessmentReference();
                long version = previous == null ? 1L : previous.version() + 1L;
                String id = previous == null ? "uncertainty:" + context.executionId() : previous.assessmentId();
                return new UncertaintyAssessment.Reference(id, version, context.executionId(), context.memoryScopeId());
            });
            return uncertaintyByReference.computeIfAbsent(reference, key -> {
                var assessment = new UncertaintyAssessment(key, at, at.plusSeconds(300),
                        "m8-b4-scenario-uncertainty:" + context.executionId() + ":" + triggerId,
                        UncertaintyAssessment.Calibration.notCalibrated(), Map.of(
                        UncertaintyDimension.EVIDENCE, new UncertaintySignal(UncertaintyDimension.EVIDENCE, 0.1, "fixture evidence", 1),
                        UncertaintyDimension.HYPOTHESIS, new UncertaintySignal(UncertaintyDimension.HYPOTHESIS, 0.1, "fixture hypothesis", 1),
                        UncertaintyDimension.PLAN, new UncertaintySignal(UncertaintyDimension.PLAN, 0.1, "fixture plan", 1)));
                return assessment;
            });
        }

        @Override public UncertaintyAssessment currentUncertaintyAssessment(CognitiveCycleOperations.CycleContext context, Instant at) {
            var reference = context.requireUncertaintyAssessmentReference();
            var assessment = uncertaintyByReference.get(reference);
            if (assessment == null || !assessment.isCurrentFor(context.executionId(), context.memoryScopeId(), at)) {
                throw new SecurityException("scenario host cannot resolve the exact current uncertainty revision");
            }
            return assessment;
        }

        @Override public MetaControlInputs metaControlInputs(CognitiveCycleOperations.CycleContext context, Instant at) {
            var uncertainty = currentUncertaintyAssessment(context, at);
            var budgetDefinition = durableBudget == null
                    ? new ResourceBudget(Map.of(), at.plusSeconds(60)) : durableBudget;
            var budget = new ResourceBudgetSnapshot(context.executionId(),
                    budgetDefinition, Map.of(), Map.of(), at);
            return new MetaControlInputs(budget, uncertainty, new ResourceUsage(Map.of()));
        }

        @Override public void recordMetaControlDecision(CognitiveCycleOperations.CycleContext context,
                MetaControlInputs inputs, MetaControlDecision decision, Instant at) { }

        @Override public PlanningRequest revalidationRequest(CognitiveCycleOperations.CycleContext context,
                CognitiveCycleDecisionServices.SelectedPlanningCandidate selected,
                WorkingMemorySnapshot latestWorkingMemory, Instant at) {
            revalidationRequests.incrementAndGet();
            var base = selected.request();
            if (approvalMode == ApprovalMode.OBSERVATION_INVALIDATES_PLAN && revalidationRequests.get() == 1) {
                worldStore.applyQualityHoldToAll(at, "new authoritative quality observation after approval");
                worldStore.addEvidence(new EvidenceReference("quality-alert",
                        "quality:post-approval:" + fixture.definition().scenarioId().replace('-', '_') + ":v1"));
            }
            if (approvalMode == ApprovalMode.DEMAND_CHANGED_THEN_DENY && revalidationRequests.get() == 1) {
                worldStore.increaseDemand(at);
                worldStore.addEvidence(new EvidenceReference("demand-observation",
                        "demand:post-approval:" + fixture.definition().scenarioId().replace('-', '_') + ":v1"));
            }
            List<ActionDescriptor> descriptors = base.availableActionDefinitions();
            if (approvalMode == ApprovalMode.STALE_PLAN_THEN_DENY && revalidationRequests.get() == 1 && !descriptors.isEmpty()) {
                ActionDescriptor descriptor = descriptors.getFirst();
                ActionDescriptor refreshed = new ActionDescriptor(descriptor.id(), descriptor.name() + " (refreshed)",
                        descriptor.description(), descriptor.category(), descriptor.effect(),
                        descriptor.requiredCapability(), descriptor.idempotencyRequired());
                descriptors = List.of(refreshed);
                worldStore.replaceActionDescriptors(descriptors);
                ReservationActionHandler registered = actionHandlers.get(refreshed.id());
                if (registered == null) {
                    throw new IllegalStateException("trusted action refresh has no matching registered M3 handler");
                }
                // The M5 trusted grounding definition and the M3 handler registry are refreshed together.
                // CognitiveCycleLifecycleServices intentionally rejects a mismatch between these two authorities.
                registered.replaceDescriptor(refreshed);
            }
            return new PlanningRequest(context.executionId(), base.objective(), worldStore.snapshot(), latestWorkingMemory,
                    worldStore.evidence(base.evidence()), descriptors, base.hardConstraints(), base.scoringProfile(), base.riskPolicy(),
                    base.authorityPolicy(), base.complexity(), base.budget(), base.requestedAt(), base.resumeSessionId(),
                    context.requireUncertaintyAssessmentReference());
        }

        @Override public List<ActionDispatchCommand<?, ?>> actionCommands(CognitiveCycleOperations.CycleContext context,
                CognitiveCycleDecisionServices.SelectedPlanningCandidate selected, Instant at) {
            var result = new ArrayList<ActionDispatchCommand<?, ?>>();
            for (PlanningAction action : selected.candidate().actions()) {
                if (!(action.simulationCommand() instanceof SimulationCommand.ReserveInventory reserve)) {
                    throw new IllegalStateException("B4-B action adapter only accepts typed inventory-reservation commands");
                }
                result.add(new ActionDispatchCommand<>(action.planningActionId(), action.descriptorId(),
                        SimulationCommand.ReserveInventory.class, String.class, reserve));
            }
            return List.copyOf(result);
        }

        @Override public CognitiveCycleOperations.ExternalObservation observeAndReconcile(
                CognitiveCycleOperations.CycleContext context, ActionExecutionBatch batch, Instant at) {
            boolean allReceiptsSucceeded = batch.status() == ActionExecutionBatch.BatchStatus.SUCCEEDED
                    && !batch.items().isEmpty()
                    && batch.items().stream().allMatch(item -> item.status() == dev.supplychain.agent.actions.api.ActionStatus.SUCCEEDED
                        || item.status() == dev.supplychain.agent.actions.api.ActionStatus.ALREADY_COMPLETED);
            boolean objectiveSatisfied = allReceiptsSucceeded && effects.objectiveSatisfied();
            if (allReceiptsSucceeded) observationsReconciled.addAndGet(batch.items().size());
            return new CognitiveCycleOperations.ExternalObservation(objectiveSatisfied
                    ? CognitiveCycleOperations.ObservationDisposition.OBJECTIVE_SATISFIED
                    : CognitiveCycleOperations.ObservationDisposition.NOT_VERIFIED,
                    "observation:" + context.executionId(), "evidence-set:post-action:" + context.executionId(),
                    objectiveSatisfied ? "M3 receipts succeeded and authoritative simulated reservations satisfy every affected order line" :
                            "M3 receipts were reconciled, but available qualified reservations do not satisfy the protected objective");
        }

        @Override public EpisodeDraft outcomeEpisode(CognitiveCycleOperations.CycleContext context,
                CognitiveCycleOperations.ExternalObservation observation, Instant at) {
            outcomeEpisodes.incrementAndGet();
            return new EpisodeDraft("Verified M8-B4 recovery " + context.requestId(),
                    "The deterministic host reconciled every action receipt for the selected recovery plan.",
                    EpisodeOutcome.SUCCESS, at.minusSeconds(30), at, List.of(), List.of(),
                    List.of(new EvidenceReference("observation", observation.observationId())),
                    new Provenance(ProvenanceSource.SIMULATOR, "m8-b4-deterministic-domain-adapter", at, at, "m8-b4-v1"));
        }

        @Override public ObjectiveSatisfactionProof objectiveSatisfactionProof(
                CognitiveCycleOperations.CycleContext context,
                CognitiveCycleOperations.ExternalObservation observation, Instant at) {
            return new ObjectiveSatisfactionProof(objectiveFingerprint(context),
                    List.of(new EvidenceReference("observation", observation.observationId())),
                    "m8-b4-scenario-verifier", "m8-b4-objective-proof-v1", at);
        }

        @Override public void recordEscalation(CognitiveCycleOperations.CycleContext context, String reason, Instant at) {
            escalations.incrementAndGet();
        }
    }

    private static final class AuthorityAdapter implements CognitiveCycleAuthorityPort {
        private final ApprovalMode mode;
        private final ScenarioFixture fixture;
        private final AtomicInteger approvalRequests = new AtomicInteger();
        private volatile ApprovalBinding currentBinding;
        private volatile String currentApprovalId;

        private AuthorityAdapter(ApprovalMode mode, ScenarioFixture fixture) {
            this.mode = mode;
            this.fixture = fixture;
        }

        @Override public CognitiveCycleOperations.AuthorizationPrepared requestApproval(
                CognitiveCycleOperations.CycleContext context, ApprovalBinding binding,
                List<ActionDescriptor> actionDescriptors, Instant at) {
            if (actionDescriptors.isEmpty()) throw new SecurityException("an approval cannot be issued for an empty action plan");
            currentBinding = binding;
            currentApprovalId = "approval:" + context.executionId() + ":" + approvalRequests.incrementAndGet();
            return new CognitiveCycleOperations.AuthorizationPrepared(
                    CognitiveCycleOperations.AuthorizationDisposition.REQUIRES_APPROVAL, true,
                    currentApprovalId, "host policy requires explicit approval for every domain write");
        }

        @Override public CognitiveCycleOperations.ApprovalResolved resolveApproval(
                CognitiveCycleOperations.CycleContext context, ApprovalBinding binding, Instant at) {
            if (!binding.equals(currentBinding)) throw new SecurityException("approval request binding changed before resolution");
            boolean reject = mode == ApprovalMode.DENIED
                    || ((mode == ApprovalMode.STALE_PLAN_THEN_DENY || mode == ApprovalMode.DEMAND_CHANGED_THEN_DENY)
                        && approvalRequests.get() > 1);
            var disposition = reject ? CognitiveCycleOperations.ApprovalDisposition.DENIED
                    : CognitiveCycleOperations.ApprovalDisposition.APPROVED;
            return new CognitiveCycleOperations.ApprovalResolved(disposition, currentApprovalId,
                    binding.candidateId(), binding.candidateSetId(), binding.intentionId(), binding.intentionVersion(),
                    context.approverId(), "approval-evidence:" + currentApprovalId,
                    disposition == CognitiveCycleOperations.ApprovalDisposition.APPROVED
                            ? "human approved the exact bound action plan" : "human declined the exact bound action plan");
        }

        @Override public boolean isApprovalStillValid(CognitiveCycleOperations.CycleContext context,
                ApprovalBinding binding, String approvalId, Instant at) {
            return mode != ApprovalMode.REVOKED && binding.equals(currentBinding)
                    && approvalId.equals(currentApprovalId);
        }

        @Override public CognitiveCycleOperations.HumanControlResolution resolveHumanControl(
                CognitiveCycleOperations.CycleContext context, Instant at) {
            return new CognitiveCycleOperations.HumanControlResolution(CognitiveCycleOperations.HumanControlAction.CONTINUE,
                    context.humanControlActorId(), context.humanControlResponseId(), "test host confirmed continuation");
        }

        @Override public boolean isTerminationAuthorized(CognitiveCycleOperations.CycleContext context,
                                                           String actorId, Instant at) { return false; }

        @Override public ActionPrincipal executionPrincipal(CognitiveCycleOperations.CycleContext context, Instant at) {
            String suffix = fixture.definition().scenarioId().replace('-', '_');
            Set<ResourceReference> fixtureLots = Set.of(
                    new ResourceReference("inventory-lot", "LOT-A-" + suffix),
                    new ResourceReference("inventory-lot", "LOT-B-" + suffix));
            return new ActionPrincipal("m8-b4-human-authorized-test-principal",
                    Set.of(CapabilityGrant.scoped(Capability.INVENTORY_RESERVE, fixtureLots)));
        }
    }

    /** Versioned authoritative domain snapshot shared by planning, revalidation and action preconditions. */
    private static final class ScenarioWorldStore {
        private dev.supplychain.agent.domain.world.SupplyChainSnapshot current;
        private List<ActionDescriptor> currentActionDescriptors = List.of();
        private final LinkedHashSet<EvidenceReference> currentEvidence = new LinkedHashSet<>();

        private ScenarioWorldStore(dev.supplychain.agent.domain.world.SupplyChainSnapshot initial,
                                   List<EvidenceReference> initialEvidence) {
            this.current = initial;
            this.currentEvidence.addAll(initialEvidence);
        }

        synchronized dev.supplychain.agent.domain.world.SupplyChainSnapshot snapshot() {
            return current;
        }

        synchronized void initializeActionDescriptors(List<ActionDescriptor> descriptors) {
            if (!currentActionDescriptors.isEmpty()) throw new IllegalStateException("scenario action definitions already initialized");
            currentActionDescriptors = List.copyOf(descriptors);
        }

        synchronized List<ActionDescriptor> actionDescriptors(List<ActionDescriptor> fallback) {
            return currentActionDescriptors.isEmpty() ? List.copyOf(fallback) : currentActionDescriptors;
        }

        synchronized void replaceActionDescriptors(List<ActionDescriptor> descriptors) {
            currentActionDescriptors = List.copyOf(descriptors);
        }

        synchronized List<EvidenceReference> evidence(List<EvidenceReference> fallback) {
            return currentEvidence.isEmpty() ? List.copyOf(fallback) : List.copyOf(currentEvidence);
        }

        synchronized void addEvidence(EvidenceReference reference) {
            currentEvidence.add(reference);
        }

        synchronized void applyQualityHoldToAll(Instant at, String reason) {
            var next = current.toBuilder().version(current.version() + 1).capturedAt(at);
            current.inventoryLots().values().stream()
                    .filter(lot -> lot.qualityStatus() == LotQualityStatus.AVAILABLE)
                    .forEach(lot -> next.replaceInventoryLot(lot.applyQualityHold(reason, at)));
            current = next.build();
        }

        synchronized void increaseDemand(Instant at) {
            var objective = current.recoveryObjectives().values().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("scenario world has no recovery objective"));
            var orderId = objective.affectedOrders().iterator().next();
            var order = current.customerOrder(orderId);
            var line = order.lines().values().iterator().next();
            Quantity increased = line.requestedQuantity().plus(Quantity.of("10", line.requestedQuantity().unit()));
            var next = current.toBuilder().version(current.version() + 1).capturedAt(at)
                    .replaceCustomerOrder(order.withLine(line.withRequestedQuantity(increased)));
            current = next.build();
        }
    }

    /** Deterministic authoritative stock/quality boundary used only by these full-graph tests. */
    private static final class ScenarioEffectStore {
        private final ScenarioFixture fixture;
        private final ScenarioWorldStore worldStore;
        private final Map<String, Quantity> availableByLot = new HashMap<>();
        private final Map<String, Quantity> reservedByOrderLine = new HashMap<>();
        private final Set<String> executedLotIds = new HashSet<>();
        private final AtomicInteger effectCount = new AtomicInteger();

        private ScenarioEffectStore(ScenarioFixture fixture, ScenarioWorldStore worldStore) {
            this.fixture = fixture;
            this.worldStore = worldStore;
            fixture.planningRequest().initialWorld().inventoryLots().values().forEach(lot ->
                    availableByLot.put(lot.id().value(), fixture.planningRequest().initialWorld()
                            .availableInventory(lot.id(), fixture.planningRequest().requestedAt())));
        }

        synchronized PreconditionCheck precondition(SimulationCommand.ReserveInventory command) {
            InventoryLot lot = worldStore.snapshot().inventoryLots().get(command.lotId());
            if (lot == null) return PreconditionCheck.reject("UNKNOWN_LOT", "reservation references an unknown inventory lot");
            if (lot.qualityStatus() != LotQualityStatus.AVAILABLE) {
                return PreconditionCheck.reject("QUALITY_HOLD", "inventory lot is not quality-released");
            }
            Quantity available = availableByLot.getOrDefault(command.lotId().value(), Quantity.zero(command.fulfillmentQuantity().unit()));
            if (available.compareTo(command.fulfillmentQuantity()) < 0) {
                return PreconditionCheck.reject("INSUFFICIENT_STOCK", "reservation exceeds current qualified unreserved stock");
            }
            return PreconditionCheck.pass();
        }

        synchronized String reserve(SimulationCommand.ReserveInventory command) {
            PreconditionCheck check = precondition(command);
            if (!check.allowed()) throw new SecurityException(check.code() + ": " + check.reason());
            String lotId = command.lotId().value();
            Quantity available = availableByLot.get(lotId);
            availableByLot.put(lotId, available.minus(command.fulfillmentQuantity()));
            String key = command.orderId().value() + "|" + command.orderLineId().value();
            Quantity previous = reservedByOrderLine.getOrDefault(key,
                    Quantity.zero(command.fulfillmentQuantity().unit()));
            reservedByOrderLine.put(key, previous.plus(command.fulfillmentQuantity()));
            executedLotIds.add(lotId);
            effectCount.incrementAndGet();
            return "simulated-reservation:" + command.reservationId().value();
        }

        synchronized boolean objectiveSatisfied() {
            var world = worldStore.snapshot();
            var objective = fixture.planningRequest().objective();
            for (var orderId : objective.affectedOrders()) {
                var order = world.customerOrder(orderId);
                for (var line : order.lines().values()) {
                    String key = orderId.value() + "|" + line.id().value();
                    Quantity initialCommitment = world.reservedFulfillmentQuantity(orderId, line.id())
                            .plus(world.committedShipmentFulfillmentQuantity(orderId, line.id()));
                    Quantity newCommitment = reservedByOrderLine.getOrDefault(key, Quantity.zero(line.requestedQuantity().unit()));
                    if (initialCommitment.plus(newCommitment).compareTo(line.requestedQuantity()) < 0) return false;
                }
            }
            return true;
        }

        synchronized Set<String> executedLotIds() { return Set.copyOf(executedLotIds); }
    }

    private static final class ReservationActionHandler implements ActionHandler<SimulationCommand.ReserveInventory, String> {
        private volatile ActionDescriptor descriptor;
        private final ScenarioEffectStore effects;

        private ReservationActionHandler(ActionDescriptor descriptor, ScenarioEffectStore effects) {
            this.descriptor = descriptor;
            this.effects = effects;
        }

        @Override public ActionDescriptor descriptor() { return descriptor; }
        void replaceDescriptor(ActionDescriptor replacement) {
            if (!descriptor.id().equals(replacement.id())) {
                throw new IllegalArgumentException("an action handler cannot be rebound to a different action id");
            }
            descriptor = replacement;
        }
        @Override public Class<SimulationCommand.ReserveInventory> inputType() { return SimulationCommand.ReserveInventory.class; }
        @Override public Class<String> outputType() { return String.class; }
        @Override public PreconditionCheck precondition(ActionContext context, SimulationCommand.ReserveInventory input) {
            if (!context.principal().principalId().equals("m8-b4-human-authorized-test-principal")) {
                return PreconditionCheck.reject("UNEXPECTED_PRINCIPAL", "controlled scenario action principal did not match");
            }
            return effects.precondition(input);
        }
        @Override public String execute(ActionContext context, SimulationCommand.ReserveInventory input) {
            if (!context.principal().principalId().equals("m8-b4-human-authorized-test-principal")) {
                throw new SecurityException("unexpected action principal in the controlled scenario host");
            }
            return effects.reserve(input);
        }
        @Override public Set<ResourceReference> resources(SimulationCommand.ReserveInventory input) {
            return Set.of(new ResourceReference("inventory-lot", input.lotId().value()));
        }
        @Override public String resultReference(String output) { return output; }
    }
}
