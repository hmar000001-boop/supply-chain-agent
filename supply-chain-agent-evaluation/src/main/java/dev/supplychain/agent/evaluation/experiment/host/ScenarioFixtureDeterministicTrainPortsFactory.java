package dev.supplychain.agent.evaluation.experiment.host;

import dev.supplychain.agent.actions.api.ActionContext;
import dev.supplychain.agent.actions.api.ActionDescriptor;
import dev.supplychain.agent.actions.api.ActionHandler;
import dev.supplychain.agent.actions.api.ActionId;
import dev.supplychain.agent.actions.api.ActionPrincipal;
import dev.supplychain.agent.actions.api.PreconditionCheck;
import dev.supplychain.agent.actions.api.ActionRegistry;
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
import dev.supplychain.agent.evaluation.planner.EvaluationPlannerFactory;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.Provenance;
import dev.supplychain.agent.memory.common.ProvenanceSource;
import dev.supplychain.agent.mechanisms.b1.MaintenanceProfile;
import dev.supplychain.agent.mechanisms.b1.B1CandidateFeatures;
import dev.supplychain.agent.memory.episodic.EpisodeId;
import dev.supplychain.agent.memory.episodic.Episode;
import dev.supplychain.agent.memory.episodic.EpisodeOutcome;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalQuery;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalResult;
import dev.supplychain.agent.memory.working.MemoryValue;
import dev.supplychain.agent.memory.working.WorkingMemoryId;
import dev.supplychain.agent.memory.working.WorkingMemorySnapshot;
import dev.supplychain.agent.memory.working.WorkingMemoryService;
import dev.supplychain.agent.mechanisms.b1.B1CandidateFeatureProvider;
import dev.supplychain.agent.mechanisms.b1.B1ScoringPolicy;
import dev.supplychain.agent.mechanisms.b1.B1SelectionRequest;
import dev.supplychain.agent.mechanisms.b1.B1SelectionResult;
import dev.supplychain.agent.mechanisms.b3.ConsumerReceiptStatus;
import dev.supplychain.agent.mechanisms.b3.InMemoryB3OutcomeEvidenceRepository;
import dev.supplychain.agent.mechanisms.b2.InMemoryRecoveryProcedureDraftRepository;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentArm;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentSandboxAudit;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import dev.supplychain.agent.evaluation.experiment.scenario.M8ExperimentScenarioFactory;
import dev.supplychain.agent.evaluation.host.R2WorkflowConfiguration;
import dev.supplychain.agent.actions.api.ActionCategory;
import dev.supplychain.agent.actions.api.ActionEffect;
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
import dev.supplychain.agent.mechanisms.b6.InMemoryResourceBudgetLedger;
import dev.supplychain.agent.mechanisms.b6.ResourceUnit;
import dev.supplychain.agent.mechanisms.b6.ResourceBudgetSnapshot;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment;
import dev.supplychain.agent.mechanisms.b6.UncertaintyDimension;
import dev.supplychain.agent.mechanisms.b6.UncertaintySignal;
import dev.supplychain.agent.planning.api.DeterministicCandidateEvaluator;
import dev.supplychain.agent.planning.api.PlanningAction;
import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.api.PlanningStrategy;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningWorldModel;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentLinkedQueue;
import dev.supplychain.agent.evaluation.experiment.host.DeterministicTrainPortsFactory;
import dev.supplychain.agent.evaluation.experiment.host.DeterministicTrainExecutionProfile;
import dev.supplychain.agent.evaluation.experiment.host.DeterministicTrainSessionConfiguration;
import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMetricCollector;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentScenarioUnit;

/**
 * Concrete deterministic per-arm TRAIN ports extracted from the full-graph scenario adapters.
 * It creates fresh mutable services, stores, consumers, planners and audit counters for every arm.
 * The request/reasoning providers are deterministic; no network/model clients or credentials are
 * configured. The authority adapter requires approval and defaults to DENIED if ever resumed.
 */
public final class ScenarioFixtureDeterministicTrainPortsFactory implements DeterministicTrainPortsFactory {
    private static final DeterministicTrainExecutionProfile PROFILE = new DeterministicTrainExecutionProfile(
            "m8-scenario-fixture-deterministic-v1",
            "scenario-fixture-ready-v1",
            "reasoning-disabled-no-llm-v1",
            "evaluation-planner-factory-v1",
            "supply-chain-simulator-v1",
            DeterministicTrainExecutionProfile.ModelExecution.DISABLED,
            true, true, true, true, true, true);

    @Override public DeterministicTrainExecutionProfile executionProfile() { return PROFILE; }

    @Override
    public DeterministicTrainSessionConfiguration create(ExperimentScenarioUnit unit,
            ExperimentMechanism mechanism, ExperimentArm arm) throws Exception {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(mechanism, "mechanism");
        Objects.requireNonNull(arm, "arm");
        PROFILE.requireAdmissibleForTrain();
        if (unit.split() != ExperimentSplit.TRAIN) throw new SecurityException("scenario fixture factory accepts TRAIN only");
        if (!M8ExperimentScenarioFactory.PROTOCOL_ID.equals(unit.protocolId()))
            throw new SecurityException("scenario fixture factory protocol mismatch");

        ScenarioFixture fixture = unit.fixture();
        ScenarioWorldStore worldStore = new ScenarioWorldStore(
                fixture.planningRequest().initialWorld(), fixture.planningRequest().evidence());
        worldStore.initializeActionDescriptors(fixture.planningRequest().availableActionDefinitions());
        ScenarioEffectStore effectStore = new ScenarioEffectStore(fixture, worldStore);
        AtomicInteger actionDispatchAttempts = new AtomicInteger();
        Clock clock = Clock.fixed(ScenarioFixtureFactory.FIXTURE_AS_OF, ZoneOffset.UTC);
        ScenarioFixtureFactory worldFactory = new ScenarioFixtureFactory(ScenarioCatalog.loadDefault());
        PlanningWorldModel worldModel = worldFactory.worldModel();
        DeterministicCandidateEvaluator evaluator = worldFactory.evaluator(worldModel);
        DecisionAdapter decision = new DecisionAdapter(fixture, worldStore);
        decision.includeFeedbackCandidates = true;
        decision.useGraphPlannerSessionId = true;

        ActionRegistry registry = new ActionRegistry();
        Map<ActionId, ReservationActionHandler> handlers = new HashMap<>();
        fixture.planningRequest().availableActionDefinitions().forEach(descriptor -> {
            ReservationActionHandler handler = new ReservationActionHandler(descriptor, effectStore, actionDispatchAttempts);
            registry.register(handler);
            handlers.put(descriptor.id(), handler);
        });
        registry.register(new R2InventoryReadTestHandler(actionDispatchAttempts));

        LifecycleAdapter lifecycle = new LifecycleAdapter(fixture, clock, ApprovalMode.DENIED,
                effectStore, worldStore, handlers, null, false);
        AuthorityAdapter authority = new AuthorityAdapter(ApprovalMode.DENIED, fixture);

        WorkspaceConsumer consumer = new WorkspaceConsumer() {
            @Override public WorkspaceConsumerReceipt consume(WorkspaceBroadcast broadcast) {
                return new WorkspaceConsumerReceipt("planner", broadcast.snapshot().id(),
                        ConsumerReceiptStatus.ACCEPTED,
                        broadcast.snapshot().entries().stream().map(entry -> entry.candidate().id()).toList(),
                        broadcast.broadcastAt(), "deterministic fixture consumer accepted the exact proposed workspace");
            }
        };
        Map<String, WorkspaceConsumer> consumers = Map.of("planner", consumer);
        List<PlanningStrategy> strategies = new EvaluationPlannerFactory(worldFactory)
                .strategiesForFullGraph(fixture).stream().map(delegate -> (PlanningStrategy) new PlanningStrategy() {
                    @Override public PlannerId id() { return delegate.id(); }
                    @Override public dev.supplychain.agent.planning.api.PlanningResult plan(PlanningRequest request) {
                        return delegate.plan(request);
                    }
                }).toList();
        RequestInterpreter interpreter = new RequestInterpreter() {
            @Override public RequestInterpretation interpret(InterpretationRequest request) {
                return new RequestInterpretation(InterpretationOutcome.READY, request.originalText(),
                        "Deterministic M8 scenario fixture interpretation");
            }
        };
        // R2 is wired so receipts and SHADOW mechanisms are present in the graph. No simulated
        // receipt is accepted as a downstream business outcome and no recovery procedure may be
        // approved/promoted by this research fixture host.
        InMemoryB3OutcomeEvidenceRepository outcomeRepository = new InMemoryB3OutcomeEvidenceRepository();
        InMemoryRecoveryProcedureDraftRepository draftRepository = new InMemoryRecoveryProcedureDraftRepository();
        MemoryScopeId expectedScope = fixture.planningRequest().workingMemory().scopeId();
        String scenarioId = fixture.definition().scenarioId();
        R2WorkflowConfiguration r2 = new R2WorkflowConfiguration(draftRepository,
                (scope, evidence, at) -> false,
                (draft, approver, approval, at) -> false,
                (context, resolvedCase, result, parent, at) -> Optional.empty(),
                outcomeRepository,
                (scope, receipt, candidate, signal, evidence, at) -> false,
                "m8-deterministic-train-r2-v1", "m8-untrusted-outcome-rejected-v1",
                null, null, WorkspaceCoalitionOutcomeAdjustment.neutral());
        List<Episode> seedEpisodes = List.of(
                verifiedScenarioEpisode(fixture, "candidate-a"),
                verifiedScenarioEpisode(fixture, "candidate-b"));
        FullGraphEvaluationHost.Ports ports = new FullGraphEvaluationHost.Ports(
                decision, lifecycle, authority, interpreter, worldModel, evaluator, strategies,
                clock, registry, consumers, null, r2, seedEpisodes);

        String conditionFingerprint = sha256(unit.effectiveInputFingerprint() + "|" + mechanism + "|" + arm
                + "|scenario-fixture-deterministic-v1");
        ExperimentSandboxAudit audit = () -> new ExperimentSandboxAudit.Snapshot(
                0, actionDispatchAttempts.get(), effectStore.effectCount.get(), 0);
        ExperimentMetricCollector collector = (experimentUnit, selectedMechanism, selectedArm, runtime, terminalState) -> {
            Map<String, Double> metrics = new java.util.TreeMap<>();
            metrics.put("runtime.b1.feature.seed.count", (double) runtime.b1MemoryFeatureRepository().snapshot().size());
            metrics.put("runtime.planner.strategy.count", (double) ports.plannerStrategies().size());
            metrics.put("runtime.workspace.consumer.count", (double) ports.workspaceConsumers().size());
            metrics.put("runtime.action.dispatch.attempts", (double) actionDispatchAttempts.get());
            metrics.put("runtime.simulated.state.effects", (double) effectStore.effectCount.get());
            metrics.put("runtime.approval.request.count", (double) authority.approvalRequests.get());
            return Map.copyOf(metrics);
        };
        return new DeterministicTrainSessionConfiguration(ports, conditionFingerprint, audit, collector,
                List.of(), WorkspaceCoalitionOutcomeAdjustment.neutral(), List.of());
    }

    private static String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

private enum ApprovalMode {
        APPROVED, DENIED, REVOKED, STALE_PLAN_THEN_DENY,
        DEMAND_CHANGED_THEN_DENY, OBSERVATION_INVALIDATES_PLAN
    }

    private record TerminalOutput(String node, CognitiveCycleState state) { }

private static Episode verifiedScenarioEpisode(ScenarioFixture fixture, String itemId) {
        MemoryScopeId scope = fixture.planningRequest().workingMemory().scopeId();
        Instant recordedAt = ScenarioFixtureFactory.FIXTURE_AS_OF.minusSeconds(10);
        Instant completedAt = recordedAt.minusSeconds(10);
        Instant startedAt = completedAt.minusSeconds(10);
        var episode = Episode.recorded(new EpisodeId(itemId), scope, "Verified recovery memory " + itemId,
                "A verified prior recovery episode used as a B1 retrieval fixture", EpisodeOutcome.SUCCESS,
                startedAt, completedAt, recordedAt, recordedAt.plus(Duration.ofDays(30)), List.of(), List.of(),
                List.of(new EvidenceReference("scenario-fixture", fixture.definition().scenarioId())),
                new Provenance(ProvenanceSource.VERIFIED_EPISODE, "r2-seed:" + itemId, completedAt,
                        recordedAt, "m8-r2-integration-fixture-v1"));
        return episode.verify("m8-r2-test-reviewer", new EvidenceReference("episode-review", "reviewed:" + itemId),
                ScenarioFixtureFactory.FIXTURE_AS_OF.minusSeconds(1));
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

private static final class DecisionAdapter implements CognitiveCycleDecisionPort,
            FullGraphEvaluationHost.WorkingMemoryServiceAwareDecisionPort {
        private final ScenarioFixture fixture;
        private final ScenarioWorldStore worldStore;
        private WorkspaceBroadcastReport lastBroadcast;
        private PlanningRequest lastPlanningRequest;
        private B1SelectionResult lastB1Selection;
        private long lastPlanningContextWorkingMemoryVersion = -1L;
        private boolean includeFeedbackCandidates;
        private boolean useGraphPlannerSessionId;
        private WorkingMemoryService workingMemoryService;

        private DecisionAdapter(ScenarioFixture fixture, ScenarioWorldStore worldStore) {
            this.fixture = fixture;
            this.worldStore = worldStore;
        }

        @Override public void attachWorkingMemoryService(WorkingMemoryService workingMemoryService) {
            // The evaluation deliberately compiles a baseline graph and then a second graph with
            // the authorized overlay. Each compilation owns its own repository-backed memory.
            this.workingMemoryService = Objects.requireNonNull(workingMemoryService, "workingMemoryService");
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
            return reference -> {
                double utility = switch (reference.itemId()) {
                    case "candidate-a" -> 2.0;
                    case "candidate-b" -> 2.3;
                    default -> throw new AssertionError("unexpected seeded B1 episodic candidate: " + reference.itemId());
                };
                return new B1CandidateFeatures(List.of(), Map.of(), utility, 1, Duration.ZERO,
                        new MaintenanceProfile(0.0, 0.0, 0.0, Duration.ofDays(1),
                                ScenarioFixtureFactory.FIXTURE_AS_OF.minusSeconds(1)));
            };
        }

        @Override public CognitiveCycleOperations.EvidenceRetrieved persistEvidenceSelection(
                CognitiveCycleOperations.CycleContext context, MemoryRetrievalResult retrieved,
                B1SelectionResult selected, Instant at) {
            lastB1Selection = selected;
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
            var candidates = new ArrayList<WorkspaceCandidate>();
            candidates.add(new WorkspaceCandidate("request-context:" + context.requestId(), scope,
                    WorkspaceCandidateKind.OBSERVATION, 2.0, 1.0, 0.0, 2,
                    Set.of("current-request"), Set.of(), true,
                    List.of(new EvidenceReference("request", context.requestId()))));
            if (includeFeedbackCandidates) {
                candidates.add(new WorkspaceCandidate("episodic:candidate-a:1", scope, WorkspaceCandidateKind.OBSERVATION,
                        5.0, 1.0, 0.0, 1, Set.of(), Set.of(), false,
                        List.of(new EvidenceReference("episodic-memory", "candidate-a"))));
                candidates.add(new WorkspaceCandidate("episodic:candidate-b:1", scope, WorkspaceCandidateKind.OBSERVATION,
                        5.4, 1.0, 0.0, 1, Set.of(), Set.of(), false,
                        List.of(new EvidenceReference("episodic-memory", "candidate-b"))));
            }
            return new WorkspaceAdmissionRequest("cycle:" + context.executionId(), scope, 10, 5,
                    Set.of("current-request"), Map.of("current-request", 1.0),
                    new WorkspaceScoringPolicy(0.0, 1.0), candidates, at,
                    context.requireUncertaintyAssessmentReference());
        }

        @Override public String experimentUnitFingerprint(CognitiveCycleOperations.CycleContext context, Instant at) {
            return fixture.fixtureFingerprint();
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
            if (workingMemoryService == null) {
                throw new IllegalStateException("full-graph evaluation decision adapter was not bound to the host working-memory service");
            }
            if (context.workingMemoryId().isBlank()) {
                throw new IllegalStateException("planning context does not identify the active working-memory snapshot");
            }
            var working = workingMemoryService.load(new WorkingMemoryId(context.workingMemoryId()));
            if (!working.scopeId().equals(memoryScope(context))
                    || working.version() != context.workingMemoryVersion()) {
                throw new IllegalStateException("planning adapter loaded a working-memory snapshot that does not match the graph context");
            }
            lastPlanningContextWorkingMemoryVersion = context.workingMemoryVersion();
            String resumeSessionId = useGraphPlannerSessionId
                    ? (context.plannerSessionId().isBlank() ? null : context.plannerSessionId())
                    : base.resumeSessionId();
            lastPlanningRequest = copy(base, working, worldStore.snapshot(),
                    worldStore.actionDescriptors(base.availableActionDefinitions()),
                    worldStore.evidence(base.evidence()), context.executionId(),
                    context.requireUncertaintyAssessmentReference(), resumeSessionId);
            return lastPlanningRequest;
        }

        private PlanningRequest copy(PlanningRequest base, WorkingMemorySnapshot working,
                                     dev.supplychain.agent.domain.world.SupplyChainSnapshot world,
                                     List<ActionDescriptor> descriptors, List<EvidenceReference> evidence, String executionId,
                                     UncertaintyAssessment.Reference uncertaintyReference, String resumeSessionId) {
            return new PlanningRequest(executionId, base.objective(), world, working, evidence,
                    descriptors, base.hardConstraints(), base.scoringProfile(), base.riskPolicy(),
                    base.authorityPolicy(), base.complexity(), base.budget(), base.requestedAt(), resumeSessionId,
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
        private final AtomicInteger childStepsExecuted = new AtomicInteger();
        private final RecoveryObjective objective;
        private final ResourceBudget durableBudget;
        private final boolean resolveB2;
        private final Map<String, UncertaintyAssessment.Reference> uncertaintyByTrigger = new java.util.HashMap<>();
        private final Map<UncertaintyAssessment.Reference, UncertaintyAssessment> uncertaintyByReference = new java.util.HashMap<>();
        private final ConcurrentLinkedQueue<IntentionDirective> scriptedIntentionDirectives = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean forceMetaControlDownshift = new AtomicBoolean();
        private InMemoryResourceBudgetLedger downshiftLedger;

        private LifecycleAdapter(ScenarioFixture fixture, Clock clock, ApprovalMode approvalMode,
                                 ScenarioEffectStore effects, ScenarioWorldStore worldStore,
                                 Map<ActionId, ReservationActionHandler> actionHandlers, ResourceBudget durableBudget,
                                 boolean resolveB2) {
            this.fixture = fixture;
            this.clock = clock;
            this.effects = effects;
            this.worldStore = worldStore;
            this.actionHandlers = Map.copyOf(actionHandlers);
            this.approvalMode = approvalMode;
            this.objective = fixture.planningRequest().objective();
            this.durableBudget = durableBudget;
            this.resolveB2 = resolveB2;
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
                    objective, "m8-b4-test-owner", new RecoveryMethodReference("cot", "candidate:initial-cot-method")));
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
            if (resolveB2) {
                return new ChildInvestigationPlan(List.of(new ChildInvestigationStep("inventory-read-proof",
                        Capability.INVENTORY_READ, "inspect the immutable scenario inventory snapshot")),
                        ResolutionDisposition.EVIDENCE_CONFIRMED, List.of("fixture inventory evidence resolves the modeled impasse"),
                        DelegationReturn.NO_METHOD_CHANGE);
            }
            // Default fixtures do not invent missing supply or evidence. The unresolved result returns
            // control to the parent intention, which the graph then escalates safely.
            return new ChildInvestigationPlan(List.of(), ResolutionDisposition.UNRESOLVED,
                    List.of(), DelegationReturn.NO_METHOD_CHANGE);
        }

        @Override public List<dev.supplychain.agent.mechanisms.b2.ResolutionFinding> executeChildInvestigationStep(
                CognitiveCycleOperations.CycleContext context,
                dev.supplychain.agent.mechanisms.b2.ImpasseResolutionCase resolutionCase,
                ChildInvestigationStep step, Instant at) {
            if (!resolveB2 || step.capability() != Capability.INVENTORY_READ) {
                throw new UnsupportedOperationException("unexpected B2 child action in B4-B workflow fixture");
            }
            childStepsExecuted.incrementAndGet();
            return List.of(new dev.supplychain.agent.mechanisms.b2.ResolutionFinding(
                    "scenario inventory snapshot confirms the investigation precondition",
                    List.of(new EvidenceReference("scenario-fixture", fixture.definition().scenarioId()))));
        }

        @Override public dev.supplychain.agent.mechanisms.b2.ParentContextSnapshot currentParentContext(
                CognitiveCycleOperations.CycleContext context, WorkingMemorySnapshot latestWorkingMemory) {
            return new dev.supplychain.agent.mechanisms.b2.ParentContextSnapshot(latestWorkingMemory.scopeId(),
                    context.requestId(), latestWorkingMemory.version(), objectiveFingerprint(context));
        }

        void enqueueIntentionDirective(IntentionDirective directive) {
            scriptedIntentionDirectives.add(Objects.requireNonNull(directive, "directive"));
        }

        void forceNextMetaControlDownshift(InMemoryResourceBudgetLedger ledger) {
            downshiftLedger = Objects.requireNonNull(ledger, "ledger");
            forceMetaControlDownshift.set(true);
        }

        @Override public IntentionDirective intentionDirective(CognitiveCycleOperations.CycleContext context,
                dev.supplychain.agent.mechanisms.b4.RecoveryIntention current, Instant at) {
            IntentionDirective scripted = scriptedIntentionDirectives.poll();
            if (scripted != null) return scripted;
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
            boolean triggerDownshift = forceMetaControlDownshift.compareAndSet(true, false);
            if (triggerDownshift) {
                if (downshiftLedger == null || durableBudget == null) {
                    throw new IllegalStateException("the B6 downshift fixture requires the same registered execution ledger and budget used by the planner guard");
                }
                var before = downshiftLedger.snapshot(context.executionId(), at);
                long remaining = before.remaining(ResourceUnit.SEARCH_EXPANSIONS);
                long targetRemaining = Math.max(1L, Math.min(19L, durableBudget.limit(ResourceUnit.SEARCH_EXPANSIONS) / 5L));
                long preconsume = Math.max(0L, remaining - targetRemaining);
                if (preconsume > 0L) {
                    String reservationId = "fixture-prior-search-work:" + context.executionId();
                    var usage = ResourceUsage.of(ResourceUnit.SEARCH_EXPANSIONS, preconsume);
                    downshiftLedger.reserve(context.executionId(), reservationId, usage, at);
                    downshiftLedger.commit(context.executionId(), reservationId, usage, at);
                }
            }
            var budgetDefinition = durableBudget == null
                    ? new ResourceBudget(Map.of(), at.plusSeconds(60)) : durableBudget;
            // When this fixture pre-consumes the execution ledger, the governor must inspect
            // that same authoritative snapshot. An empty synthetic usage map would hide the
            // low-margin condition and make the full-graph downshift test a false positive.
            var budget = downshiftLedger != null && durableBudget != null
                    ? downshiftLedger.snapshot(context.executionId(), at)
                    : new ResourceBudgetSnapshot(context.executionId(),
                            budgetDefinition, Map.of(), Map.of(), at);
            ResourceUsage required = triggerDownshift
                    ? ResourceUsage.of(ResourceUnit.SEARCH_EXPANSIONS, 1L) : new ResourceUsage(Map.of());
            return new MetaControlInputs(budget, uncertainty, required);
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
        private final AtomicInteger dispatchAttempts;

        private ReservationActionHandler(ActionDescriptor descriptor, ScenarioEffectStore effects, AtomicInteger dispatchAttempts) {
            this.descriptor = descriptor;
            this.effects = effects;
            this.dispatchAttempts = dispatchAttempts;
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
            dispatchAttempts.incrementAndGet();
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

private static final class R2InventoryReadTestHandler implements ActionHandler<String, String> {
        private static final ActionId ID = new ActionId("m8-test:inventory-read");
        private static final ActionDescriptor DESCRIPTOR = new ActionDescriptor(ID, "Verify inventory snapshot",
                "Read-only fixture action reference for a typed B2 recovery draft", ActionCategory.PERCEPTION,
                ActionEffect.READ_ONLY, Capability.INVENTORY_READ, false);
        private final AtomicInteger dispatchAttempts;
        private R2InventoryReadTestHandler(AtomicInteger dispatchAttempts) { this.dispatchAttempts = dispatchAttempts; }
        @Override public ActionDescriptor descriptor() { return DESCRIPTOR; }
        @Override public Class<String> inputType() { return String.class; }
        @Override public Class<String> outputType() { return String.class; }
        @Override public String execute(ActionContext context, String input) { dispatchAttempts.incrementAndGet(); return "verified-snapshot:" + input; }
    }
}
