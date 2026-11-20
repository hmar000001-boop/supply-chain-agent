package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.actions.api.ActionCategory;
import dev.supplychain.agent.actions.api.ActionContext;
import dev.supplychain.agent.actions.api.ActionDescriptor;
import dev.supplychain.agent.actions.api.ActionDispatcher;
import dev.supplychain.agent.actions.api.ActionEffect;
import dev.supplychain.agent.actions.api.ActionFailure;
import dev.supplychain.agent.actions.api.ActionFailureCode;
import dev.supplychain.agent.actions.api.ActionHandler;
import dev.supplychain.agent.actions.api.ActionId;
import dev.supplychain.agent.actions.api.ActionLedgerClaim;
import dev.supplychain.agent.actions.api.ActionPrincipal;
import dev.supplychain.agent.actions.api.ActionReceipt;
import dev.supplychain.agent.actions.api.ActionRegistry;
import dev.supplychain.agent.actions.api.ActionResult;
import dev.supplychain.agent.actions.api.ActionStatus;
import dev.supplychain.agent.actions.api.Capability;
import dev.supplychain.agent.actions.api.CapabilityAuthorizer;
import dev.supplychain.agent.actions.api.CapabilityGrant;
import dev.supplychain.agent.actions.api.IdempotencyLedgerKey;
import dev.supplychain.agent.actions.api.ResourceReference;
import dev.supplychain.agent.domain.recovery.RecoveryConstraint;
import dev.supplychain.agent.domain.recovery.RecoveryObjective;
import dev.supplychain.agent.domain.recovery.RecoveryObjectiveId;
import dev.supplychain.agent.domain.recovery.RecoveryObjectiveStatus;
import dev.supplychain.agent.domain.orders.CustomerOrderId;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresDatabaseConfig;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresPoolSettings;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresPooledDataSourceFactory;
import dev.supplychain.agent.infrastructure.postgres.migration.PostgresMigrationRunner;
import dev.supplychain.agent.infrastructure.postgres.checkpoint.PostgresLangGraph4jCheckpointSaverFactory;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntention;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntentionId;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntentionService;
import dev.supplychain.agent.mechanisms.b4.RecoveryMethodReference;
import dev.supplychain.agent.mechanisms.b6.ResourceBudget;
import dev.supplychain.agent.mechanisms.b6.ResourceUnit;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleAuthorityPort;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphResult;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.PostgresSaver;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;

/**
 * M8-B4-C process-boundary tests. These tests use a real LangGraph4j PostgreSQL saver and the
 * production PostgreSQL intention, approval, resource-budget and M3 operation-ledger adapters.
 * A second Hikari pool and a newly compiled graph represent replacement of the application host.
 */
@EnabledIfSystemProperty(named = "phase0.postgres", matches = "true")
class M8B4CRestartReconciliationIntegrationTest {
    private static final Instant NOW = Instant.now().minusSeconds(5).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    private static final PostgresPoolSettings POOL = new PostgresPoolSettings(4, 0, 5_000, 3_000, 30_000, 600_000, 0);
    private static final ActionId ACTION_ID = new ActionId("m8b4c.test.inventory.reserve");
    private static final String PRINCIPAL_ID = "m8b4c-recovery-operator";
    private static final String CORRELATION_ID = "m8b4c-correlation";
    private static final String IDEMPOTENCY_KEY = "m8b4c-reserve-order-line";
    private static final String INPUT = "LOT-M8B4C:2";

    @Test
    void interruptedWorkflowReloadsApprovalIntentionBudgetAndReconcilesAmbiguousWriteAfterPoolReplacement() throws Exception {
        var database = PostgresDatabaseConfig.fromEnvironment(System.getenv());
        String scope = "m8b4c-process-restart-" + UUID.randomUUID();
        String execution = "execution-" + UUID.randomUUID();
        String threadId = PostgresLangGraph4jCheckpointSaverFactory.threadIdForExecution(scope, execution);
        var config = RunnableConfig.builder().threadId(threadId).build();
        var provider = new ProviderSystem();
        String approvalId;
        String intentionId;

        // Process 1: create durable intent/approval/budget, pause, approve, then encounter an
        // external timeout after the provider has committed. The graph checkpoints before reconcile.
        try (var firstDataSource = PostgresPooledDataSourceFactory.create(database, POOL)) {
            assertEquals(PostgresMigrationRunner.supportedMigrationCount(),
                    new PostgresMigrationRunner(firstDataSource).migrate().totalMigrations());
            var firstHost = new WorkflowHost(firstDataSource, scope, execution, provider);
            var builder = workflowGraph(firstHost);
            PostgresSaver saver = PostgresLangGraph4jCheckpointSaverFactory.create(firstDataSource, builder);
            var firstGraph = builder.compile(CompileConfig.builder().checkpointSaver(saver)
                    .interruptBefore("await-approval", "reconcile")
                    .releaseThread(false).recursionLimit(32).build());

            var initial = firstGraph.stream(GraphInput.args(Map.of("scopeId", scope, "executionId", execution)), config);
            for (var ignored : initial) { /* drive until the approval boundary */ }
            assertTrue(GraphResult.from(initial).isInterruptionMetadata());
            var approvalState = firstGraph.getState(config).state();
            approvalId = value(approvalState, "approvalId");
            intentionId = value(approvalState, "intentionId");
            assertEquals("PENDING", firstHost.approvals.find(scope, approvalId).orElseThrow().status().name());

            var pending = firstHost.approvals.find(scope, approvalId).orElseThrow();
            firstHost.approvals.decide(scope, approvalId, pending.revision(), true,
                    "human-reviewer", "response-after-interrupt", "approved exact recovery action", NOW.plusSeconds(1));
            var approvalUpdate = firstGraph.updateState(config, Map.of("approvalResponseId", "response-after-interrupt"), null);
            var dispatchRun = firstGraph.stream(GraphInput.resume(), approvalUpdate);
            for (var ignored : dispatchRun) { /* drive through dispatch until reconciliation boundary */ }
            assertTrue(GraphResult.from(dispatchRun).isInterruptionMetadata());
            var interrupted = firstGraph.getState(config).state();
            assertEquals("UNKNOWN_OUTCOME", value(interrupted, "actionStatus"));
            assertFalse(value(interrupted, "operationId").isBlank());
            assertEquals(1, provider.effectCount(), "the external provider committed once before reporting timeout");
        }

        // Process 2: every host-side object is rebuilt against a new pool. Only PostgreSQL and the
        // simulated external provider survive; no graph state or repository object is shared.
        try (var replacementDataSource = PostgresPooledDataSourceFactory.create(database, POOL)) {
            var replacementHost = new WorkflowHost(replacementDataSource, scope, execution, provider);
            var replacementBuilder = workflowGraph(replacementHost);
            PostgresSaver replacementSaver = PostgresLangGraph4jCheckpointSaverFactory.create(replacementDataSource, replacementBuilder);
            var replacementGraph = replacementBuilder.compile(CompileConfig.builder().checkpointSaver(replacementSaver)
                    .interruptBefore("await-approval", "reconcile")
                    .releaseThread(false).recursionLimit(32).build());

            var checkpoint = replacementGraph.getState(config).state();
            assertEquals("UNKNOWN_OUTCOME", value(checkpoint, "actionStatus"));
            assertEquals(intentionId, value(checkpoint, "intentionId"));
            assertEquals(1, provider.effectCount(), "restarting the application must not itself repeat the external effect");

            RecoveryIntention restored = replacementHost.intentions.get(new RecoveryIntentionId(intentionId));
            assertEquals(value(checkpoint, "objectiveFingerprint"), restored.objectiveFingerprint());
            assertEquals(Long.parseLong(value(checkpoint, "intentionVersion")), restored.version());
            assertEquals("ACTIVE", restored.status().name());
            assertEquals(Set.of(RecoveryConstraint.NO_OVER_ALLOCATION,
                    RecoveryConstraint.APPROVAL_REQUIRED_FOR_EXTERNAL_COMMITMENT), restored.protectedConstraints());
            assertTrue(replacementHost.approvals.isCurrentlyValid(scope, value(checkpoint, "approvalId"),
                    replacementHost.bindingFrom(checkpoint), NOW.plusSeconds(2)),
                    "the replacement host must re-read and validate the exact persisted approval binding");
            var pendingBudget = replacementHost.budgets.snapshot(execution, NOW.plusSeconds(2));
            assertEquals(2L, pendingBudget.reserved().get(ResourceUnit.MODEL_CALLS));
            assertEquals(6L, pendingBudget.remaining(ResourceUnit.MODEL_CALLS));
            var blockedReplay = replacementHost.dispatcher.dispatch(ACTION_ID, String.class, String.class,
                    replacementHost.actionContext(), INPUT);
            assertEquals(ActionStatus.FAILED, blockedReplay.status());
            assertTrue(blockedReplay.failureOptional().orElseThrow().outcomeUnknown(),
                    "an UNKNOWN_OUTCOME must block blind redispatch after host replacement");
            assertEquals(1, provider.effectCount(), "the blocked replay must not call the external provider again");

            var terminal = replacementGraph.invokeFinal(GraphInput.resume(), config)
                    .orElseThrow(() -> new AssertionError("replacement graph returned no terminal result"));
            assertEquals(END, terminal.node());
            assertEquals("RECONCILED", terminal.state().value("workflowOutcome").orElse(""));
            assertEquals("ALREADY_COMPLETED", terminal.state().value("replayStatus").orElse(""));
            assertEquals(1, provider.effectCount(), "reconciliation must acknowledge the receipt without dispatching again");

            var finalBudget = replacementHost.budgets.snapshot(execution, NOW.plusSeconds(4));
            assertEquals(2L, finalBudget.consumed().get(ResourceUnit.MODEL_CALLS));
            assertEquals(0L, finalBudget.reserved().get(ResourceUnit.MODEL_CALLS));
            assertEquals(6L, finalBudget.remaining(ResourceUnit.MODEL_CALLS));
            var finalClaim = replacementHost.ledger.inspect(replacementHost.ledgerKey(), replacementHost.requestFingerprint())
                    .orElseThrow();
            assertEquals(ActionLedgerClaim.Status.COMPLETED, finalClaim.status());
        }
    }

    @Test
    void authoritativeNotAppliedReconciliationAllowsOneSafeRetryAfterHostReplacement() throws Exception {
        var database = PostgresDatabaseConfig.fromEnvironment(System.getenv());
        String scope = "m8b4c-not-applied-" + UUID.randomUUID();
        try (var migrationDataSource = PostgresPooledDataSourceFactory.create(database, POOL)) {
            new PostgresMigrationRunner(migrationDataSource).migrate();
        }
        AtomicInteger handlerCalls = new AtomicInteger();
        ProviderSystem provider = new ProviderSystem();
        String idempotencyKey = "m8b4c-retryable-reservation";
        ActionId retryActionId = new ActionId("m8b4c.test.retryable-write");
        var firstHandler = actionHandler(retryActionId.value(), (context, input) -> {
            handlerCalls.incrementAndGet();
            throw new IllegalStateException("provider timed out before applying the reservation");
        });
        var firstRegistry = new ActionRegistry().register(firstHandler);
        var firstContext = ActionContext.create("execution-retry", "correlation-retry", principal(), NOW,
                "idempotency-retry");
        var key = new IdempotencyLedgerKey(PRINCIPAL_ID, retryActionId, "idempotency-retry");
        String fingerprint = sha256(firstHandler.requestFingerprint("LOT-RETRY:1"));
        try (var firstPool = PostgresPooledDataSourceFactory.create(database, POOL)) {
            var firstLedger = new PostgresActionExecutionLedger(firstPool, scope,
                    M8B4ReliabilityIntegrationTest.actionReceiptCodec(),
                    M8B4ReliabilityIntegrationTest.actionFailureCodec(), Duration.ofSeconds(30));
            var first = new ActionDispatcher(firstRegistry, new CapabilityAuthorizer(), firstLedger)
                    .dispatch(retryActionId, String.class, String.class, firstContext, "LOT-RETRY:1");
            assertEquals(ActionStatus.FAILED, first.status());
            assertTrue(first.failureOptional().orElseThrow().outcomeUnknown());
            assertEquals(0, provider.effectCount());
            var unknown = firstLedger.inspect(key, fingerprint).orElseThrow();
            assertEquals(ActionLedgerClaim.Status.UNKNOWN_OUTCOME, unknown.status());

            // An authoritative provider read proves that the first attempt had no effect.
            assertTrue(provider.find(idempotencyKey).isEmpty());
            firstLedger.reconcileUnknownAsNotApplied(key,
                    new ActionFailure(ActionFailureCode.TRANSIENT_FAILURE,
                            "provider query confirmed no reservation exists", true, false));
            assertTrue(firstLedger.inspect(key, fingerprint).isEmpty(),
                    "a definitively NOT_APPLIED failure is absent to inspection so fenced retry can proceed");
        }
        // A replacement host rebuilds its handler registry, principal context, ledger and pool.
        // The provider's state survives independently of both application hosts.
        try (var replacementPool = PostgresPooledDataSourceFactory.create(database, POOL)) {
            var replacementHandler = actionHandler(retryActionId.value(), (context, input) -> {
                handlerCalls.incrementAndGet();
                provider.commitOnce(idempotencyKey, context.operationIdOptional().orElseThrow(), input);
                return "committed:" + input;
            });
            var replacementRegistry = new ActionRegistry().register(replacementHandler);
            var replacementContext = ActionContext.create("execution-retry", "correlation-retry", principal(), NOW,
                    "idempotency-retry");
            var replacementLedger = new PostgresActionExecutionLedger(replacementPool, scope,
                    M8B4ReliabilityIntegrationTest.actionReceiptCodec(),
                    M8B4ReliabilityIntegrationTest.actionFailureCodec(), Duration.ofSeconds(30));
            var replacementDispatcher = new ActionDispatcher(replacementRegistry, new CapabilityAuthorizer(), replacementLedger);
            var retried = replacementDispatcher.dispatch(retryActionId, String.class, String.class,
                    replacementContext, "LOT-RETRY:1");
            assertEquals(ActionStatus.SUCCEEDED, retried.status());
            assertEquals(1, provider.effectCount());
            var duplicate = replacementDispatcher.dispatch(retryActionId, String.class, String.class,
                    replacementContext, "LOT-RETRY:1");
            assertEquals(ActionStatus.ALREADY_COMPLETED, duplicate.status());
            assertEquals(2, handlerCalls.get(), "one unknown attempt plus one approved retry; never a third execution");
            assertEquals(1, provider.effectCount());
        }
    }

    @Test
    void independentDispatchersWithSameIdempotencyKeyProduceOneConcurrentExternalEffect() throws Exception {
        var database = PostgresDatabaseConfig.fromEnvironment(System.getenv());
        String scope = "m8b4c-dispatch-race-" + UUID.randomUUID();
        try (var dataSourceA = PostgresPooledDataSourceFactory.create(database, POOL);
             var dataSourceB = PostgresPooledDataSourceFactory.create(database, POOL)) {
            new PostgresMigrationRunner(dataSourceA).migrate();
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger effects = new AtomicInteger();
            String actionId = "m8b4c.test.concurrent-write";
            var handlerA = actionHandler(actionId, (context, input) -> {
                effects.incrementAndGet();
                entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test release timeout");
                return "committed:" + input;
            });
            var handlerB = actionHandler(actionId, (context, input) -> {
                effects.incrementAndGet();
                return "committed:" + input;
            });
            var registryA = new ActionRegistry().register(handlerA);
            var registryB = new ActionRegistry().register(handlerB);
            var ledgerA = new PostgresActionExecutionLedger(dataSourceA, scope,
                    M8B4ReliabilityIntegrationTest.actionReceiptCodec(),
                    M8B4ReliabilityIntegrationTest.actionFailureCodec(), Duration.ofSeconds(30));
            var ledgerB = new PostgresActionExecutionLedger(dataSourceB, scope,
                    M8B4ReliabilityIntegrationTest.actionReceiptCodec(),
                    M8B4ReliabilityIntegrationTest.actionFailureCodec(), Duration.ofSeconds(30));
            var dispatcherA = new ActionDispatcher(registryA, new CapabilityAuthorizer(), ledgerA);
            var dispatcherB = new ActionDispatcher(registryB, new CapabilityAuthorizer(), ledgerB);
            var contextA = ActionContext.create("execution-race", "correlation-race", principal(), NOW,
                    "idempotency-race");
            var contextB = ActionContext.create("execution-race", "correlation-race", principal(), NOW,
                    "idempotency-race");
            try (var workers = Executors.newSingleThreadExecutor()) {
                var first = workers.submit(() -> dispatcherA.dispatch(handlerA.descriptor().id(), String.class,
                        String.class, contextA, "LOT-RACE:1"));
                assertTrue(entered.await(5, TimeUnit.SECONDS), "first dispatcher did not acquire the operation lease");
                var concurrent = dispatcherB.dispatch(handlerB.descriptor().id(), String.class, String.class,
                        contextB, "LOT-RACE:1");
                assertEquals(ActionStatus.IN_PROGRESS, concurrent.status());
                release.countDown();
                assertEquals(ActionStatus.SUCCEEDED, first.get(10, TimeUnit.SECONDS).status());
            } finally {
                release.countDown();
            }
            var replay = dispatcherB.dispatch(handlerB.descriptor().id(), String.class, String.class,
                    contextB, "LOT-RACE:1");
            assertEquals(ActionStatus.ALREADY_COMPLETED, replay.status());
            assertEquals(1, effects.get(), "the two independent dispatchers must execute one business effect");
        }
    }

    @Test
    void concurrentIntentionUpdatesUseVersionCasAndPreserveTheProtectedObjective() throws Exception {
        var database = PostgresDatabaseConfig.fromEnvironment(System.getenv());
        String scope = "m8b4c-intention-race-" + UUID.randomUUID();
        try (var dataSourceA = PostgresPooledDataSourceFactory.create(database, POOL);
             var dataSourceB = PostgresPooledDataSourceFactory.create(database, POOL)) {
            new PostgresMigrationRunner(dataSourceA).migrate();
            var repositoryA = new PostgresRecoveryIntentionRepository(dataSourceA, scope,
                    M8B4ReliabilityIntegrationTest.recoveryIntentionCodec());
            var repositoryB = new PostgresRecoveryIntentionRepository(dataSourceB, scope,
                    M8B4ReliabilityIntegrationTest.recoveryIntentionCodec());
            RecoveryObjective objective = objective("order-intention-race-" + UUID.randomUUID());
            RecoveryIntentionId intentionId = RecoveryIntentionId.random();
            RecoveryIntention original = new RecoveryIntentionService(repositoryA).create(intentionId,
                    objective, new MemoryScopeId(scope), "planner-owner",
                    new RecoveryMethodReference("cot", "candidate:initial"), NOW);
            RecoveryIntention left = original.requestReconsideration("worker-left", NOW.plusSeconds(1));
            RecoveryIntention right = original.requestReconsideration("worker-right", NOW.plusSeconds(1));
            CountDownLatch start = new CountDownLatch(1);
            try (var workers = Executors.newFixedThreadPool(2)) {
                var a = workers.submit(() -> compareAfter(start, repositoryA, intentionId, original.version(), left));
                var b = workers.submit(() -> compareAfter(start, repositoryB, intentionId, original.version(), right));
                start.countDown();
                assertTrue(a.get(10, TimeUnit.SECONDS) ^ b.get(10, TimeUnit.SECONDS),
                        "exactly one process may advance a given intention version");
            }
            RecoveryIntention current = new RecoveryIntentionService(repositoryB).get(intentionId);
            assertEquals(1, current.version());
            assertEquals(original.objectiveFingerprint(), current.objectiveFingerprint());
            assertEquals(original.protectedConstraints(), current.protectedConstraints());
            try (var connection = dataSourceB.getConnection(); var query = connection.prepareStatement(
                    "SELECT count(*) FROM recovery_intention_history WHERE scope_id=? AND intention_id=?")) {
                query.setString(1, scope);
                query.setString(2, intentionId.value());
                try (var row = query.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals(2L, row.getLong(1), "history contains initial version plus exactly one winning CAS");
                }
            }
        }
    }

    private static StateGraph<RestartState> workflowGraph(WorkflowHost host) throws Exception {
        return new StateGraph<>(RestartState.SCHEMA, RestartState::new)
                .addNode("prepare", node_async(state -> host.prepare(state)))
                .addNode("await-approval", node_async(state -> host.approveAndDispatch(state)))
                .addNode("reconcile", node_async(state -> host.reconcile(state)))
                .addNode("verify", node_async(state -> host.verify(state)))
                .addEdge(START, "prepare")
                .addEdge("prepare", "await-approval")
                .addEdge("await-approval", "reconcile")
                .addEdge("reconcile", "verify")
                .addEdge("verify", END);
    }

    private static final class RestartState extends AgentState {
        private static final String[] FIELDS = {"scopeId", "executionId", "intentionId", "intentionVersion",
                "objectiveFingerprint", "approvalId", "requestId", "candidateSetId", "candidateId",
                "planFingerprint", "approvalResponseId", "actionStatus", "operationId", "workflowOutcome",
                "replayStatus", "budgetRemaining"};
        private static final Map<String, Channel<?>> SCHEMA = schema();
        private RestartState(Map<String, Object> initialData) { super(initialData); }
        private static Map<String, Channel<?>> schema() {
            var values = new java.util.LinkedHashMap<String, Channel<?>>();
            for (String field : FIELDS) values.put(field, Channels.base(() -> ""));
            return Map.copyOf(values);
        }
    }

    private static final class WorkflowHost {
        private final String scope;
        private final String execution;
        private final ProviderSystem provider;
        private final PostgresRecoveryIntentionRepository intentionRepository;
        private final RecoveryIntentionService intentions;
        private final PostgresApprovalStore approvals;
        private final PostgresResourceBudgetLedger budgets;
        private final PostgresActionExecutionLedger ledger;
        private final ActionRegistry registry;
        private final ActionHandler<String, String> handler;
        private final ActionDispatcher dispatcher;
        private final ActionPrincipal principal = principal();
        private final RecoveryIntentionId intentionId;
        private final String approvalId;

        private WorkflowHost(DataSource dataSource, String scope, String execution, ProviderSystem provider) {
            this.scope = scope;
            this.execution = execution;
            this.provider = provider;
            this.intentionRepository = new PostgresRecoveryIntentionRepository(dataSource, scope,
                    M8B4ReliabilityIntegrationTest.recoveryIntentionCodec());
            this.intentions = new RecoveryIntentionService(intentionRepository);
            this.approvals = new PostgresApprovalStore(dataSource, scope);
            this.budgets = new PostgresResourceBudgetLedger(dataSource, scope);
            this.ledger = new PostgresActionExecutionLedger(dataSource, scope,
                    M8B4ReliabilityIntegrationTest.actionReceiptCodec(),
                    M8B4ReliabilityIntegrationTest.actionFailureCodec(), Duration.ofSeconds(30));
            this.intentionId = new RecoveryIntentionId("intention:" + execution);
            this.approvalId = "approval:" + execution;
            this.handler = actionHandler(ACTION_ID.value(), (context, input) -> {
                provider.commitOnce(IDEMPOTENCY_KEY, context.operationIdOptional().orElseThrow(), input);
                throw new IllegalStateException("provider timed out after its external commit");
            });
            this.registry = new ActionRegistry().register(handler);
            this.dispatcher = new ActionDispatcher(registry, new CapabilityAuthorizer(), ledger);
        }


        Map<String, Object> prepare(RestartState state) {
            String inputScope = value(state, "scopeId");
            String inputExecution = value(state, "executionId");
            if (!scope.equals(inputScope) || !execution.equals(inputExecution)) {
                throw new SecurityException("workflow input is outside the runtime scope/execution");
            }
            RecoveryObjective objective = objective("order:" + execution);
            RecoveryIntention created = intentions.create(intentionId, objective, new MemoryScopeId(scope),
                    "planner-owner", new RecoveryMethodReference("mcts", "candidate:recovery-1"), NOW);
            var binding = new CognitiveCycleAuthorityPort.ApprovalBinding(
                    "request:" + execution, execution, scope, "candidate-set:" + execution,
                    "candidate:recovery-1", intentionId.value(), created.version(), "a".repeat(64));
            approvals.create(scope, approvalId, binding, "requester-service",
                    "{\"capabilities\":[\"INVENTORY_RESERVE\"]}",
                    "{\"warehouse\":\"WH-M8B4C\"}", "evidence:inventory-v1", "approval-policy-v1",
                    NOW.plusSeconds(600), NOW);
            budgets.register(execution, new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 8L), NOW.plusSeconds(3600)));
            budgets.reserve(execution, "planning-reservation", ResourceUsage.of(ResourceUnit.MODEL_CALLS, 2), NOW);
            return Map.of("intentionId", created.id().value(), "intentionVersion", Long.toString(created.version()),
                    "objectiveFingerprint", created.objectiveFingerprint(), "approvalId", approvalId,
                    "requestId", binding.requestId(), "candidateSetId", binding.candidateSetId(),
                    "candidateId", binding.candidateId(), "planFingerprint", binding.actionPlanFingerprint());
        }

        Map<String, Object> approveAndDispatch(RestartState state) {
            var binding = bindingFrom(state);
            String approval = value(state, "approvalId");
            ApprovalRecord persistedDecision = approvals.find(scope, approval)
                    .orElseThrow(() -> new SecurityException("persisted approval was not found after resume"));
            if (persistedDecision.status() != ApprovalRecord.Status.APPROVED
                    || !persistedDecision.responseId().equals(value(state, "approvalResponseId"))) {
                throw new SecurityException("resume response does not match the persisted human approval decision");
            }
            if (!approvals.isCurrentlyValid(scope, approval, binding, NOW.plusSeconds(2))) {
                throw new SecurityException("persisted approval is absent, revoked, expired or bound to another plan");
            }
            RecoveryIntention intention = intentions.get(new RecoveryIntentionId(value(state, "intentionId")));
            if (intention.version() != binding.intentionVersion()
                    || !intention.objectiveFingerprint().equals(value(state, "objectiveFingerprint"))) {
                throw new SecurityException("persisted intention no longer matches the approved objective/version");
            }
            var budget = budgets.snapshot(execution, NOW.plusSeconds(2));
            if (budget.reserved().get(ResourceUnit.MODEL_CALLS) != 2L) {
                throw new IllegalStateException("expected reserved B6 work was not restored before dispatch");
            }
            ActionContext context = actionContext();
            // The production dispatcher supplies its claimed operation ID to the provider handler.
            ActionResult<String> result = dispatcher.dispatch(ACTION_ID, String.class, String.class, context, INPUT);
            if (result.status() != ActionStatus.FAILED
                    || !result.failureOptional().orElseThrow().outcomeUnknown()) {
                throw new IllegalStateException("fault-injection provider did not produce the expected UNKNOWN_OUTCOME");
            }
            ActionLedgerClaim claim = ledger.inspect(ledgerKey(), requestFingerprint()).orElseThrow();
            if (claim.status() != ActionLedgerClaim.Status.UNKNOWN_OUTCOME) {
                throw new IllegalStateException("M3 ledger did not persist UNKNOWN_OUTCOME");
            }
            return Map.of("actionStatus", claim.status().name(), "operationId", claim.operationId());
        }

        Map<String, Object> reconcile(RestartState state) {
            ActionLedgerClaim unknown = ledger.inspect(ledgerKey(), requestFingerprint()).orElseThrow();
            if (unknown.status() != ActionLedgerClaim.Status.UNKNOWN_OUTCOME) {
                throw new IllegalStateException("reconciliation requires a persisted UNKNOWN_OUTCOME ledger row");
            }
            ProviderEffect effect = provider.find(IDEMPOTENCY_KEY)
                    .orElseThrow(() -> new IllegalStateException("provider cannot confirm the external outcome"));
            if (!effect.input().equals(INPUT) || !effect.operationId().equals(unknown.operationId())) {
                throw new SecurityException("provider reconciliation evidence does not match the claimed M3 operation");
            }
            ActionReceipt receipt = new ActionReceipt(ACTION_ID, unknown.operationId(), PRINCIPAL_ID,
                    CORRELATION_ID, NOW, NOW.plusSeconds(3), "provider://reservation/" + IDEMPOTENCY_KEY,
                    Map.of("reconciled", "true", "source", "authoritative-provider-read"));
            ledger.reconcileUnknownAsCompleted(ledgerKey(), receipt);
            ActionResult<String> replay = dispatcher.dispatch(ACTION_ID, String.class, String.class, actionContext(), INPUT);
            if (replay.status() != ActionStatus.ALREADY_COMPLETED) {
                throw new IllegalStateException("reconciled external operation was not acknowledged as already completed");
            }
            var committedBudget = budgets.commit(execution, "planning-reservation",
                    ResourceUsage.of(ResourceUnit.MODEL_CALLS, 2), NOW.plusSeconds(3));
            if (committedBudget.consumed().get(ResourceUnit.MODEL_CALLS) != 2L) {
                throw new IllegalStateException("B6 actual usage was not committed exactly once");
            }
            return Map.of("workflowOutcome", "RECONCILED", "replayStatus", replay.status().name(),
                    "actionStatus", ActionLedgerClaim.Status.COMPLETED.name());
        }

        Map<String, Object> verify(RestartState state) {
            RecoveryIntention intention = intentions.get(new RecoveryIntentionId(value(state, "intentionId")));
            if (!intention.objectiveFingerprint().equals(value(state, "objectiveFingerprint"))) {
                throw new SecurityException("restarted workflow changed its protected objective");
            }
            if (!approvals.isCurrentlyValid(scope, value(state, "approvalId"), bindingFrom(state), NOW.plusSeconds(4))) {
                throw new SecurityException("approval no longer matches the resumed workflow");
            }
            ActionLedgerClaim completed = ledger.inspect(ledgerKey(), requestFingerprint()).orElseThrow();
            if (completed.status() != ActionLedgerClaim.Status.COMPLETED || provider.effectCount() != 1) {
                throw new IllegalStateException("final reconciliation invariants were not satisfied");
            }
            var snapshot = budgets.snapshot(execution, NOW.plusSeconds(4));
            if (snapshot.consumed().get(ResourceUnit.MODEL_CALLS) != 2L
                    || snapshot.reserved().get(ResourceUnit.MODEL_CALLS) != 0L) {
                throw new IllegalStateException("B6 budget was not settled after restart");
            }
            return Map.of("workflowOutcome", "RECONCILED", "replayStatus", "ALREADY_COMPLETED",
                    "budgetRemaining", Long.toString(snapshot.remaining(ResourceUnit.MODEL_CALLS)));
        }

        CognitiveCycleAuthorityPort.ApprovalBinding bindingFrom(RestartState state) {
            return new CognitiveCycleAuthorityPort.ApprovalBinding(value(state, "requestId"), execution, scope,
                    value(state, "candidateSetId"), value(state, "candidateId"), value(state, "intentionId"),
                    Long.parseLong(value(state, "intentionVersion")), value(state, "planFingerprint"));
        }
        IdempotencyLedgerKey ledgerKey() { return new IdempotencyLedgerKey(PRINCIPAL_ID, ACTION_ID, IDEMPOTENCY_KEY); }
        String requestFingerprint() { return sha256(handler.requestFingerprint(INPUT)); }
        ActionContext actionContext() { return ActionContext.create(execution, CORRELATION_ID, principal, NOW, IDEMPOTENCY_KEY); }
    }

    private record ProviderEffect(String operationId, String input) { }
    private static final class ProviderSystem {
        private final ConcurrentHashMap<String, ProviderEffect> effects = new ConcurrentHashMap<>();
        void commitOnce(String idempotencyKey, String operationId, String input) {
            effects.putIfAbsent(idempotencyKey, new ProviderEffect(operationId, input));
        }
        java.util.Optional<ProviderEffect> find(String idempotencyKey) {
            return java.util.Optional.ofNullable(effects.get(idempotencyKey));
        }
        int effectCount() { return effects.size(); }
    }

    @FunctionalInterface private interface StringOperation { String apply(ActionContext context, String input) throws Exception; }
    private static ActionHandler<String, String> actionHandler(String id, StringOperation operation) {
        ActionDescriptor descriptor = new ActionDescriptor(new ActionId(id), "M8-B4-C external write",
                "Fault-injection adapter; not a production supplier integration", ActionCategory.GROUNDING,
                ActionEffect.DOMAIN_WRITE, Capability.INVENTORY_RESERVE, true);
        return new ActionHandler<>() {
            @Override public ActionDescriptor descriptor() { return descriptor; }
            @Override public Class<String> inputType() { return String.class; }
            @Override public Class<String> outputType() { return String.class; }
            @Override public Set<ResourceReference> resources(String input) {
                return Set.of(new ResourceReference("warehouse", "WH-M8B4C"));
            }
            @Override public String execute(ActionContext context, String input) {
                try { return operation.apply(context, input); }
                catch (RuntimeException ex) { throw ex; }
                catch (Exception ex) { throw new IllegalStateException("fault-injection handler failed", ex); }
            }
            @Override public String requestFingerprint(String input) { return "m8b4c-reserve:" + input; }
        };
    }

    private static ActionPrincipal principal() {
        return new ActionPrincipal(PRINCIPAL_ID, Set.of(CapabilityGrant.scoped(Capability.INVENTORY_RESERVE,
                Set.of(new ResourceReference("warehouse", "WH-M8B4C")))));
    }

    private static RecoveryObjective objective(String orderId) {
        return new RecoveryObjective(new RecoveryObjectiveId("objective:" + orderId),
                "Restore affected order lines", Set.of(new CustomerOrderId(orderId)), NOW.plusSeconds(3600),
                Set.of(RecoveryConstraint.NO_OVER_ALLOCATION,
                        RecoveryConstraint.APPROVAL_REQUIRED_FOR_EXTERNAL_COMMITMENT),
                RecoveryObjectiveStatus.ACTIVE, 0);
    }

    private static boolean compareAfter(CountDownLatch start, PostgresRecoveryIntentionRepository repository,
                                        RecoveryIntentionId id, long expectedVersion, RecoveryIntention replacement)
            throws InterruptedException {
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("CAS race did not start");
        return repository.compareAndSet(id, expectedVersion, replacement);
    }

    private static String value(org.bsc.langgraph4j.state.AgentState state, String key) {
        return state.value(key).map(Object::toString).orElse("");
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
