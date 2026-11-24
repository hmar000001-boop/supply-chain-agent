package dev.supplychain.agent.app.durable;

import dev.supplychain.agent.actions.SupplyChainActionCatalog;
import dev.supplychain.agent.actions.api.ActionDispatcher;
import dev.supplychain.agent.actions.api.ActionRegistry;
import dev.supplychain.agent.actions.api.CapabilityAuthorizer;
import dev.supplychain.agent.actions.external.grounding.SupplyChainSimulationWorld;
import dev.supplychain.agent.actions.external.perception.SupplyChainPerceptionPort;
import dev.supplychain.agent.actions.internal.ReasoningProvider;
import dev.supplychain.agent.app.cognitive.CognitiveCycleApplicationGraph;
import dev.supplychain.agent.core.interpretation.RequestInterpreter;
import dev.supplychain.agent.infrastructure.postgres.PostgresAgentPersistence;
import dev.supplychain.agent.infrastructure.postgres.PostgresMemoryPersistence;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresDatabaseConfig;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresPoolSettings;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresPooledDataSourceFactory;
import dev.supplychain.agent.infrastructure.postgres.checkpoint.PostgresLangGraph4jCheckpointSaverFactory;
import dev.supplychain.agent.infrastructure.postgres.memory.PostgresRecoveryProcedureArtifactStore;
import dev.supplychain.agent.infrastructure.postgres.runtime.JsonCodec;
import dev.supplychain.agent.infrastructure.postgres.runtime.PostgresB1MemoryFeatureRepository;
import dev.supplychain.agent.infrastructure.postgres.runtime.PostgresRuntimeCodecs;
import dev.supplychain.agent.infrastructure.postgres.runtime.PostgresRuntimePersistence;
import dev.supplychain.agent.infrastructure.postgres.runtime.PostgresUncertaintyCalibrationRepository;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.episodic.EpisodicMemoryService;
import dev.supplychain.agent.memory.procedural.ProceduralMemoryService;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalService;
import dev.supplychain.agent.memory.semantic.SemanticMemoryService;
import dev.supplychain.agent.memory.working.WorkingMemoryService;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateFactory;
import dev.supplychain.agent.mechanisms.b1.B1MemoryFeatureLearningService;
import dev.supplychain.agent.mechanisms.b1.B1MemoryFeatureState;
import dev.supplychain.agent.mechanisms.b1.B1RetrievalSelector;
import dev.supplychain.agent.mechanisms.b1.ExponentialActivationLatencyModel;
import dev.supplychain.agent.mechanisms.b1.RecencyFrequencyActivationEstimator;
import dev.supplychain.agent.mechanisms.b2.DeterministicImpasseDetector;
import dev.supplychain.agent.mechanisms.b2.ImpasseResolutionService;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedureDraft;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedureDraftService;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedurePromotionService;
import dev.supplychain.agent.mechanisms.b3.B3OutcomeEvidenceService;
import dev.supplychain.agent.mechanisms.b3.WorkspaceAdmissionService;
import dev.supplychain.agent.mechanisms.b3.WorkspaceBroadcastService;
import dev.supplychain.agent.mechanisms.b3.WorkspaceCoalitionCompetitionPolicy;
import dev.supplychain.agent.mechanisms.b3.WorkspaceCoalitionOutcomeAdjustment;
import dev.supplychain.agent.mechanisms.b3.WorkspaceConsumer;
import dev.supplychain.agent.mechanisms.b3.WorkspaceReceiptRecorder;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntentionService;
import dev.supplychain.agent.mechanisms.b6.MetaControlGovernor;
import dev.supplychain.agent.mechanisms.b6.MetaControlPolicy;
import dev.supplychain.agent.mechanisms.b6.ResourceBudget;
import dev.supplychain.agent.mechanisms.b6.UncertaintyCalibrationProfile;
import dev.supplychain.agent.mechanisms.b6.UncertaintyCalibrationService;
import dev.supplychain.agent.mechanisms.b6.UncertaintyCalibrationTrainer;
import dev.supplychain.agent.planning.api.CandidateEvaluator;
import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.api.PlanningStrategy;
import dev.supplychain.agent.planning.api.PlanningWorldModel;
import dev.supplychain.agent.planning.routing.DefaultPlannerSelectionPolicy;
import dev.supplychain.agent.planning.routing.PlannerMethodResolver;
import dev.supplychain.agent.planning.routing.PlannerRouter;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleGraph;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleState;
import dev.supplychain.agent.runtime.cognitive.ResourceBudgetExecutionGuard;
import dev.supplychain.agent.runtime.cognitive.ResourceBudgetedActionExecution;
import dev.supplychain.agent.runtime.cognitive.decision.CognitiveCycleDecisionPort;
import dev.supplychain.agent.runtime.cognitive.decision.CognitiveCycleDecisionServices;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleAuthorityPort;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecycleServices;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphResult;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Durable, application-level composition of the complete cognitive graph. Unlike the evaluation
 * host, every persistent CoALA/M3/M5/M6/B2/B3/B4/B6 store is constructed from one PostgreSQL pool.
 * The B3 workspace remains intentionally ephemeral and cycle-scoped; LangGraph checkpoints are
 * durable and use the same DataSource as the business repositories.
 */
public final class DurableCognitiveApplicationHost implements AutoCloseable {
    private static final Duration DEFAULT_WORKING_MEMORY_TTL = Duration.ofDays(1);
    private static final Duration DEFAULT_UNVERIFIED_EPISODE_RETENTION = Duration.ofDays(7);

    /** Trusted host adapters. No repository/store is accepted here: the host constructs them. */
    public record Ports(
            MemoryScopeId memoryScopeId,
            CognitiveCycleDecisionPort decisionDomain,
            CognitiveCycleLifecyclePort lifecycleDomain,
            CognitiveCycleAuthorityPort authority,
            RequestInterpreter interpreter,
            PlanningWorldModel worldModel,
            CandidateEvaluator candidateEvaluator,
            List<PlanningStrategy> plannerStrategies,
            Clock clock,
            Function<ResourceBudgetExecutionGuard, ReasoningProvider> reasoningProviderFactory,
            SupplyChainPerceptionPort perception,
            SupplyChainSimulationWorld simulationWorld,
            Map<String, WorkspaceConsumer> workspaceConsumers,
            Function<String, ResourceBudget> authoritativeBudgetProvider,
            PostgresRuntimeCodecs runtimeCodecs,
            JsonCodec<B1MemoryFeatureState> b1FeatureCodec,
            JsonCodec<RecoveryProcedureDraft> recoveryDraftCodec,
            JsonCodec<dev.supplychain.agent.mechanisms.b3.WorkspaceReceiptEvidence> b3ReceiptCodec,
            JsonCodec<dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeEvidence> b3OutcomeCodec,
            JsonCodec<UncertaintyCalibrationProfile> uncertaintyCalibrationCodec,
            DurableR2Configuration r2,
            MetaControlPolicy metaControlPolicy,
            Duration workingMemoryTtl,
            Duration unverifiedEpisodeRetention) {
        public Ports {
            Objects.requireNonNull(memoryScopeId, "memoryScopeId");
            Objects.requireNonNull(decisionDomain, "decisionDomain");
            Objects.requireNonNull(lifecycleDomain, "lifecycleDomain");
            Objects.requireNonNull(authority, "authority");
            Objects.requireNonNull(interpreter, "interpreter");
            Objects.requireNonNull(worldModel, "worldModel");
            Objects.requireNonNull(candidateEvaluator, "candidateEvaluator");
            plannerStrategies = List.copyOf(Objects.requireNonNull(plannerStrategies, "plannerStrategies"));
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(reasoningProviderFactory, "reasoningProviderFactory");
            Objects.requireNonNull(perception, "perception");
            Objects.requireNonNull(simulationWorld, "simulationWorld");
            workspaceConsumers = Map.copyOf(Objects.requireNonNull(workspaceConsumers, "workspaceConsumers"));
            workspaceConsumers.forEach((id, consumer) -> {
                if (id == null || id.isBlank() || consumer == null) throw new IllegalArgumentException("invalid workspace consumer registration");
            });
            Objects.requireNonNull(authoritativeBudgetProvider, "authoritativeBudgetProvider");
            Objects.requireNonNull(runtimeCodecs, "runtimeCodecs");
            Objects.requireNonNull(b1FeatureCodec, "b1FeatureCodec");
            Objects.requireNonNull(recoveryDraftCodec, "recoveryDraftCodec");
            Objects.requireNonNull(b3ReceiptCodec, "b3ReceiptCodec");
            Objects.requireNonNull(b3OutcomeCodec, "b3OutcomeCodec");
            Objects.requireNonNull(r2, "r2");
            Objects.requireNonNull(metaControlPolicy, "metaControlPolicy");
            positive(workingMemoryTtl, "workingMemoryTtl");
            positive(unverifiedEpisodeRetention, "unverifiedEpisodeRetention");
            List<PlannerId> actual = plannerStrategies.stream().map(PlanningStrategy::id).toList();
            if (actual.size() != PlannerId.values().length || actual.stream().distinct().count() != PlannerId.values().length
                    || !actual.containsAll(List.of(PlannerId.values()))) {
                throw new IllegalArgumentException("durable host requires exactly one planner strategy for every PlannerId");
            }
        }

        public static Ports productionDefaults(
                MemoryScopeId memoryScopeId,
                CognitiveCycleDecisionPort decisionDomain,
                CognitiveCycleLifecyclePort lifecycleDomain,
                CognitiveCycleAuthorityPort authority,
                RequestInterpreter interpreter,
                PlanningWorldModel worldModel,
                CandidateEvaluator candidateEvaluator,
                List<PlanningStrategy> plannerStrategies,
                Clock clock,
                Function<ResourceBudgetExecutionGuard, ReasoningProvider> reasoningProviderFactory,
                SupplyChainPerceptionPort perception,
                SupplyChainSimulationWorld simulationWorld,
                Map<String, WorkspaceConsumer> workspaceConsumers,
                Function<String, ResourceBudget> authoritativeBudgetProvider,
                PostgresRuntimeCodecs runtimeCodecs,
                JsonCodec<B1MemoryFeatureState> b1FeatureCodec,
                JsonCodec<RecoveryProcedureDraft> recoveryDraftCodec,
                JsonCodec<dev.supplychain.agent.mechanisms.b3.WorkspaceReceiptEvidence> b3ReceiptCodec,
                JsonCodec<dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeEvidence> b3OutcomeCodec,
                DurableR2Configuration r2) {
            return new Ports(memoryScopeId, decisionDomain, lifecycleDomain, authority, interpreter, worldModel,
                    candidateEvaluator, plannerStrategies, clock, reasoningProviderFactory, perception, simulationWorld,
                    workspaceConsumers, authoritativeBudgetProvider, runtimeCodecs, b1FeatureCodec,
                    recoveryDraftCodec, b3ReceiptCodec, b3OutcomeCodec, null, r2,
                    new MetaControlPolicy(0.4, 0.8, 0.2), DEFAULT_WORKING_MEMORY_TTL,
                    DEFAULT_UNVERIFIED_EPISODE_RETENTION);
        }
    }

    public record CycleRequest(String requestId, String executionId, MemoryScopeId scopeId, String request) {
        public CycleRequest {
            requestId = required(requestId, "requestId");
            executionId = required(executionId, "executionId");
            scopeId = Objects.requireNonNull(scopeId, "scopeId");
            request = required(request, "request");
        }
    }

    /**
     * A closed set of host-ingress responses. Identity-bearing values must be resolved by a trusted
     * ingress/authentication adapter before construction; the graph then revalidates them through
     * its authority/evidence ports. This API cannot mutate scope, plan, budget, approval decision,
     * action principal or execution state.
     */
    public sealed interface CycleResumeResponse permits ClarificationReply, EvidenceReply,
            HumanControlReply, ApprovalReply { }

    public record ClarificationReply(String clarification) implements CycleResumeResponse {
        public ClarificationReply { clarification = required(clarification, "clarification"); }
    }

    public record EvidenceReply(String hostVerifiedResponseId) implements CycleResumeResponse {
        public EvidenceReply { hostVerifiedResponseId = required(hostVerifiedResponseId, "hostVerifiedResponseId"); }
    }

    public record HumanControlReply(String hostVerifiedResponseId, String hostAuthenticatedActorId)
            implements CycleResumeResponse {
        public HumanControlReply {
            hostVerifiedResponseId = required(hostVerifiedResponseId, "hostVerifiedResponseId");
            hostAuthenticatedActorId = required(hostAuthenticatedActorId, "hostAuthenticatedActorId");
        }
    }

    public record ApprovalReply(String hostVerifiedResponseId, String hostAuthenticatedApproverId)
            implements CycleResumeResponse {
        public ApprovalReply {
            hostVerifiedResponseId = required(hostVerifiedResponseId, "hostVerifiedResponseId");
            hostAuthenticatedApproverId = required(hostAuthenticatedApproverId, "hostAuthenticatedApproverId");
        }
    }

    /** Last executed node and checkpoint state, including whether execution paused at an interrupt. */
    public record CycleRunResult(String lastExecutedNode, boolean interrupted, CognitiveCycleState state) {
        public CycleRunResult {
            lastExecutedNode = required(lastExecutedNode, "lastExecutedNode");
            state = Objects.requireNonNull(state, "state");
        }
    }

    private final PostgresAgentPersistence persistence;
    private final PostgresRuntimePersistence runtimePersistence;
    private final CompiledGraph<CognitiveCycleState> graph;
    private final ActionRegistry actionRegistry;
    private final WorkingMemoryService workingMemory;
    private final EpisodicMemoryService episodicMemory;
    private final SemanticMemoryService semanticMemory;
    private final ProceduralMemoryService proceduralMemory;
    private final ResourceBudgetExecutionGuard budgetGuard;
    private final RecoveryProcedureDraftService recoveryDrafts;
    private final RecoveryProcedurePromotionService recoveryProcedurePromotion;
    private final B3OutcomeEvidenceService b3OutcomeEvidence;
    private final UncertaintyCalibrationService uncertaintyCalibration;
    private final boolean ownsPersistence;
    private final MemoryScopeId memoryScopeId;

    private DurableCognitiveApplicationHost(PostgresAgentPersistence persistence,
            PostgresRuntimePersistence runtimePersistence, CompiledGraph<CognitiveCycleState> graph,
            ActionRegistry actionRegistry, WorkingMemoryService workingMemory,
            EpisodicMemoryService episodicMemory, SemanticMemoryService semanticMemory,
            ProceduralMemoryService proceduralMemory, ResourceBudgetExecutionGuard budgetGuard,
            RecoveryProcedureDraftService recoveryDrafts, RecoveryProcedurePromotionService recoveryProcedurePromotion,
            B3OutcomeEvidenceService b3OutcomeEvidence, UncertaintyCalibrationService uncertaintyCalibration,
            boolean ownsPersistence, MemoryScopeId memoryScopeId) {
        this.persistence = persistence; this.runtimePersistence = runtimePersistence; this.graph = graph;
        this.actionRegistry = actionRegistry; this.workingMemory = workingMemory; this.episodicMemory = episodicMemory;
        this.semanticMemory = semanticMemory; this.proceduralMemory = proceduralMemory; this.budgetGuard = budgetGuard;
        this.recoveryDrafts = recoveryDrafts; this.recoveryProcedurePromotion = recoveryProcedurePromotion;
        this.b3OutcomeEvidence = b3OutcomeEvidence; this.uncertaintyCalibration = uncertaintyCalibration;
        this.ownsPersistence = ownsPersistence;
        this.memoryScopeId = Objects.requireNonNull(memoryScopeId, "memoryScopeId");
    }

    /** Opens the pooled PostgreSQL connection, applies V001..latest migrations and assembles the full host. */
    public static DurableCognitiveApplicationHost open(Map<String, String> environment, Ports ports) throws Exception {
        Objects.requireNonNull(environment, "environment");
        PostgresDatabaseConfig config = PostgresDatabaseConfig.fromEnvironment(environment);
        PostgresAgentPersistence persistence = new PostgresAgentPersistence(
                PostgresPooledDataSourceFactory.create(config, PostgresPoolSettings.productionDefaults()));
        try { return assemble(persistence, ports, true); }
        catch (Exception | Error failure) { persistence.close(); throw failure; }
    }

    /** Uses an externally managed DataSource wrapped by PostgresAgentPersistence; the caller retains ownership. */
    public static DurableCognitiveApplicationHost assemble(PostgresAgentPersistence persistence, Ports ports) throws Exception {
        return assemble(persistence, ports, false);
    }

    private static DurableCognitiveApplicationHost assemble(PostgresAgentPersistence persistence, Ports ports,
                                                             boolean ownsPersistence) throws Exception {
        Objects.requireNonNull(persistence, "persistence"); Objects.requireNonNull(ports, "ports");
        persistence.migrate();
        PostgresMemoryPersistence memory = persistence.memory();
        var wm = new WorkingMemoryService(memory.workingMemoryRepository(), ports.workingMemoryTtl());
        var episodic = new EpisodicMemoryService(memory.episodeRepository(), ports.unverifiedEpisodeRetention());
        var semantic = new SemanticMemoryService(memory.semanticFactRepository(),
                dev.supplychain.agent.memory.semantic.SemanticAdmissionPolicy.conservativeDefault());
        var procedural = new ProceduralMemoryService(memory.proceduralMemoryRepository());
        var retrieval = new MemoryRetrievalService(memory.workingMemoryRepository(), memory.episodeRepository(),
                memory.semanticFactRepository(), memory.proceduralMemoryRepository());

        MemoryScopeId scope = ports.memoryScopeId();
        PostgresRuntimePersistence runtime = persistence.runtime(scope.value(), ports.runtimeCodecs());
        ResourceBudgetExecutionGuard guard = runtime.newResourceBudgetExecutionGuard(
                ports.authoritativeBudgetProvider(), ports.clock(), Duration.ofMinutes(2));
        ReasoningProvider reasoningProvider = Objects.requireNonNull(
                ports.reasoningProviderFactory().apply(guard), "reasoningProviderFactory returned null");
        ActionRegistry actions = SupplyChainActionCatalog.create(retrieval, wm, episodic, semantic, procedural, scope,
                reasoningProvider, ports.perception(), ports.simulationWorld());

        var impasses = new ImpasseResolutionService(new DeterministicImpasseDetector(), runtime.impasseCases());
        var b1FeatureRepository = new PostgresB1MemoryFeatureRepository(persistence.dataSource(), scope.value(), ports.b1FeatureCodec());
        var b1Learning = new B1MemoryFeatureLearningService(b1FeatureRepository);
        var r2 = ports.r2();
        var recoveryDraftRepository = persistence.recoveryProcedureDrafts(scope.value(), ports.recoveryDraftCodec());
        var b3Repository = persistence.b3OutcomeEvidence(scope.value(), ports.b3ReceiptCodec(), ports.b3OutcomeCodec());
        var b3Evidence = new B3OutcomeEvidenceService(b3Repository, r2.downstreamOutcomeEvidenceVerifier(),
                r2.admissionPolicyVersion(), r2.outcomeVerifierId(), b1Learning);
        WorkspaceReceiptRecorder receiptRecorder = b3Evidence;
        var recoveryDraftService = new RecoveryProcedureDraftService(impasses, actions, recoveryDraftRepository,
                r2.recoveryDraftEvidenceVerifier(), r2.recoveryProcedureApprovalAuthority());
        var artifactStore = new PostgresRecoveryProcedureArtifactStore(persistence.dataSource());
        var recoveryPromotion = new RecoveryProcedurePromotionService(recoveryDraftService, procedural,
                artifactStore, actions, r2.recoveryDraftEvidenceVerifier());

        var b1Selector = new B1RetrievalSelector(new RecencyFrequencyActivationEstimator(0.5, 1.0),
                new ExponentialActivationLatencyModel(100, 0.5, 1, 10_000),
                r2.activeB1FeedbackPolicy() == null
                        ? dev.supplychain.agent.mechanisms.feedback.CandidateOutcomeAdjustment.neutral()
                        : r2.activeB1FeedbackPolicy());
        WorkspaceCoalitionOutcomeAdjustment coalitionAdjustment = r2.activeB3CoalitionFeedbackPolicy() == null
                ? WorkspaceCoalitionOutcomeAdjustment.neutral() : r2.activeB3CoalitionFeedbackPolicy();
        // B3 workspace snapshots are intentionally per-cycle and ephemeral; durable receipts/outcomes live in PostgreSQL.
        var workspaceAdmission = new WorkspaceAdmissionService(new dev.supplychain.agent.mechanisms.b3.InMemoryWorkspaceRepository(),
                r2.activeB1FeedbackPolicy() == null
                        ? dev.supplychain.agent.mechanisms.feedback.CandidateOutcomeAdjustment.neutral()
                        : r2.activeB1FeedbackPolicy(), WorkspaceCoalitionCompetitionPolicy.standard(), coalitionAdjustment);
        var workspaceBroadcast = new WorkspaceBroadcastService(ports.workspaceConsumers(), receiptRecorder);
        var decisionServices = new CognitiveCycleDecisionServices(retrieval, new B1MemoryCandidateFactory(), b1Selector,
                workspaceAdmission, workspaceBroadcast,
                new PlannerRouter(ports.plannerStrategies(), new DefaultPlannerSelectionPolicy()),
                ports.worldModel(), ports.candidateEvaluator(), runtime.planningArtifacts(), ports.decisionDomain(),
                ports.clock(), guard, wm, PlannerMethodResolver.canonical(), recoveryPromotion, b1Learning);

        var intentionService = new RecoveryIntentionService(runtime.recoveryIntentions());
        UncertaintyCalibrationService uncertaintyCalibration = ports.uncertaintyCalibrationCodec() == null ? null
                : new UncertaintyCalibrationService(new PostgresUncertaintyCalibrationRepository(
                        persistence.dataSource(), scope.value(), ports.uncertaintyCalibrationCodec()),
                        new UncertaintyCalibrationTrainer());
        var lifecycleServices = new CognitiveCycleLifecycleServices(wm, episodic, impasses, intentionService,
                new MetaControlGovernor(ports.metaControlPolicy()),
                new ActionDispatcher(actions, new CapabilityAuthorizer(), runtime.actionLedger(),
                        new ResourceBudgetedActionExecution(guard)), actions, ports.interpreter(), decisionServices,
                ports.lifecycleDomain(), ports.authority(), runtime.authorizedActionPlans(), runtime.impasseChildArtifacts(),
                runtime.actionExecutionBatches(), runtime.observations(), runtime.childInvestigationSteps(), guard,
                ports.clock(), recoveryDraftService, r2.recoveryDraftRequestFactory(), uncertaintyCalibration);

        StateGraph<CognitiveCycleState> graphBuilder = CognitiveCycleApplicationGraph.buildFull(
                decisionServices, lifecycleServices, guard);
        BaseCheckpointSaver saver = PostgresLangGraph4jCheckpointSaverFactory.create(persistence.dataSource(), graphBuilder);
        CompiledGraph<CognitiveCycleState> graph = CognitiveCycleApplicationGraph.compileFull(
                decisionServices, lifecycleServices, graphBuilder, saver, guard);
        return new DurableCognitiveApplicationHost(persistence, runtime, graph, actions, wm, episodic,
                semantic, procedural, guard, recoveryDraftService, recoveryPromotion, b3Evidence,
                uncertaintyCalibration, ownsPersistence, scope);
    }

    /** Starts one distinct top-level cycle. Execution IDs map deterministically to durable checkpoint threads. */
    public Optional<CycleRunResult> start(CycleRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        requireBoundScope(request.scopeId());
        String threadId = threadId(request.scopeId().value(), request.executionId());
        var config = RunnableConfig.builder().threadId(threadId).build();
        Map<String, Object> input = Map.of(
                CognitiveCycleState.REQUEST_ID, request.requestId(),
                CognitiveCycleState.EXECUTION_ID, request.executionId(),
                CognitiveCycleState.MEMORY_SCOPE_ID, request.scopeId().value(),
                CognitiveCycleState.REQUEST, request.request());
        return streamAndSnapshot(GraphInput.args(input), config);
    }

    /**
     * Resumes a previously checkpointed cycle after supplying authenticated host/user response fields.
     * The same deterministic thread ID is required; this method never creates a new execution identity.
     */
    public Optional<CycleRunResult> resume(String scopeId, String executionId,
                                           CycleResumeResponse response) throws Exception {
        requireBoundScope(new MemoryScopeId(scopeId));
        String thread = threadId(scopeId, executionId);
        RunnableConfig config = RunnableConfig.builder().threadId(thread).build();
        RunnableConfig updated = graph.updateState(config, resumeUpdates(response), null);
        return streamAndSnapshot(GraphInput.resume(), updated);
    }

    public String threadId(String scopeId, String executionId) {
        requireBoundScope(new MemoryScopeId(scopeId));
        return PostgresLangGraph4jCheckpointSaverFactory.threadIdForExecution(scopeId, executionId);
    }

    public MemoryScopeId memoryScopeId() { return memoryScopeId; }
    public PostgresAgentPersistence persistence() { return persistence; }
    public PostgresRuntimePersistence runtimePersistence() { return runtimePersistence; }
    public CompiledGraph<CognitiveCycleState> graph() { return graph; }
    public ActionRegistry actionRegistry() { return actionRegistry; }
    public WorkingMemoryService workingMemory() { return workingMemory; }
    public EpisodicMemoryService episodicMemory() { return episodicMemory; }
    public SemanticMemoryService semanticMemory() { return semanticMemory; }
    public ProceduralMemoryService proceduralMemory() { return proceduralMemory; }
    public ResourceBudgetExecutionGuard resourceBudgetGuard() { return budgetGuard; }
    public RecoveryProcedureDraftService recoveryDrafts() { return recoveryDrafts; }
    public RecoveryProcedurePromotionService recoveryProcedurePromotion() { return recoveryProcedurePromotion; }
    public B3OutcomeEvidenceService b3OutcomeEvidence() { return b3OutcomeEvidence; }
    public Optional<UncertaintyCalibrationService> uncertaintyCalibration() { return Optional.ofNullable(uncertaintyCalibration); }

    @Override public void close() {
        if (ownsPersistence) persistence.close();
    }

    private Optional<CycleRunResult> streamAndSnapshot(GraphInput input, RunnableConfig config) throws Exception {
        var stream = graph.stream(input, config);
        String lastExecutedNode = "graph-start";
        for (var output : stream) lastExecutedNode = output.node();
        boolean interrupted = GraphResult.from(stream).isInterruptionMetadata();
        var snapshot = graph.getState(config);
        return Optional.of(new CycleRunResult(lastExecutedNode, interrupted, snapshot.state()));
    }

    private static Map<String, Object> resumeUpdates(CycleResumeResponse response) {
        Objects.requireNonNull(response, "response");
        if (response instanceof ClarificationReply reply) {
            return Map.of(CognitiveCycleState.CLARIFICATION_CONTEXT, reply.clarification());
        }
        if (response instanceof EvidenceReply reply) {
            return Map.of(CognitiveCycleState.EVIDENCE_RESPONSE_ID, reply.hostVerifiedResponseId());
        }
        if (response instanceof HumanControlReply reply) {
            return Map.of(CognitiveCycleState.HUMAN_CONTROL_RESPONSE_ID, reply.hostVerifiedResponseId(),
                    CognitiveCycleState.HUMAN_CONTROL_ACTOR_ID, reply.hostAuthenticatedActorId());
        }
        if (response instanceof ApprovalReply reply) {
            return Map.of(CognitiveCycleState.APPROVAL_RESPONSE_ID, reply.hostVerifiedResponseId(),
                    CognitiveCycleState.APPROVER_ID, reply.hostAuthenticatedApproverId());
        }
        throw new IllegalArgumentException("unsupported cognitive-cycle resume response type: " + response.getClass().getName());
    }

    private void requireBoundScope(MemoryScopeId requested) {
        if (!memoryScopeId.equals(Objects.requireNonNull(requested, "scopeId"))) {
            throw new SecurityException("durable cognitive host is bound to a different memory scope");
        }
    }

    private static void positive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) throw new IllegalArgumentException(name + " must be positive");
    }
    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
