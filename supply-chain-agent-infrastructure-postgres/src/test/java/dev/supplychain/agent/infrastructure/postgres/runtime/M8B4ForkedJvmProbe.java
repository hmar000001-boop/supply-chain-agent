package dev.supplychain.agent.infrastructure.postgres.runtime;

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
import dev.supplychain.agent.domain.orders.CustomerOrderId;
import dev.supplychain.agent.domain.recovery.RecoveryConstraint;
import dev.supplychain.agent.domain.recovery.RecoveryObjective;
import dev.supplychain.agent.domain.recovery.RecoveryObjectiveId;
import dev.supplychain.agent.domain.recovery.RecoveryObjectiveStatus;
import dev.supplychain.agent.infrastructure.postgres.checkpoint.PostgresLangGraph4jCheckpointSaverFactory;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresDatabaseConfig;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresPoolSettings;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresPooledDataSourceFactory;
import dev.supplychain.agent.infrastructure.postgres.migration.PostgresMigrationRunner;
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

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/** Main entry point executed by two independent Java processes from the B4-D forked-JVM test. */
public final class M8B4ForkedJvmProbe {
    private static final PostgresPoolSettings POOL = new PostgresPoolSettings(3, 0, 5_000, 3_000, 30_000, 600_000, 0);
    private static final ActionId ACTION_ID = new ActionId("m8b4d.process.external-write");
    private static final String PRINCIPAL_ID = "m8b4d-process-principal";
    private static final String CORRELATION_ID = "m8b4d-process-correlation";
    private static final String IDEMPOTENCY_KEY = "m8b4d-process-idempotency";
    private static final String INPUT = "LOT-PROCESS:2";

    private M8B4ForkedJvmProbe() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 6) throw new IllegalArgumentException("expected mode, scope, execution, thread, marker and epochMillis");
        String mode = args[0];
        String scope = args[1];
        String execution = args[2];
        String threadId = args[3];
        Path marker = Path.of(args[4]);
        Instant baseTime = Instant.ofEpochMilli(Long.parseLong(args[5])).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var database = PostgresDatabaseConfig.fromEnvironment(System.getenv());
        try (var dataSource = PostgresPooledDataSourceFactory.create(database, POOL)) {
            if ("write".equals(mode)) {
                new PostgresMigrationRunner(dataSource).migrate();
                runFirstProcess(dataSource, scope, execution, threadId, marker, baseTime);
            } else if ("verify".equals(mode)) {
                runReplacementProcess(dataSource, scope, execution, threadId, marker, baseTime);
            } else {
                throw new IllegalArgumentException("unknown mode: " + mode);
            }
        }
    }

    private static void runFirstProcess(DataSource dataSource, String scope, String execution,
                                        String threadId, Path marker, Instant baseTime) throws Exception {
        var host = new ProcessHost(dataSource, scope, execution, marker, baseTime);
        var builder = graph(host);
        PostgresSaver saver = PostgresLangGraph4jCheckpointSaverFactory.create(dataSource, builder);
        var compiled = builder.compile(CompileConfig.builder().checkpointSaver(saver)
                .interruptBefore("await-approval", "reconcile").releaseThread(false).recursionLimit(24).build());
        var config = RunnableConfig.builder().threadId(threadId).build();
        var initial = compiled.stream(GraphInput.args(Map.of("scopeId", scope, "executionId", execution)), config);
        for (var ignored : initial) { /* drive the graph to the approval boundary */ }
        if (!GraphResult.from(initial).isInterruptionMetadata()) {
            throw new IllegalStateException("first JVM did not stop at the approval checkpoint");
        }
        var waiting = compiled.getState(config).state();
        if (!value(waiting, "status").equals("WAITING_FOR_APPROVAL")) {
            throw new IllegalStateException("first JVM checkpoint did not retain the approval state");
        }
        var pending = host.approvals.find(scope, value(waiting, "approvalId"))
                .orElseThrow(() -> new IllegalStateException("approval was not persisted before process exit"));
        host.approvals.decide(scope, pending.approvalId(), pending.revision(), true,
                "m8b4d-human-reviewer", "m8b4d-response", "approved exact action binding", baseTime.plusSeconds(1));
        var update = compiled.updateState(config, Map.of("approvalResponseId", "m8b4d-response"), null);
        var dispatchRun = compiled.stream(GraphInput.resume(), update);
        for (var ignored : dispatchRun) { /* external commit, timeout and checkpoint before reconciliation */ }
        if (!GraphResult.from(dispatchRun).isInterruptionMetadata()) {
            throw new IllegalStateException("first JVM did not persist the ambiguous external outcome checkpoint");
        }
        var interrupted = compiled.getState(config).state();
        if (!value(interrupted, "actionStatus").equals(ActionLedgerClaim.Status.UNKNOWN_OUTCOME.name())) {
            throw new IllegalStateException("first JVM did not persist UNKNOWN_OUTCOME in graph state");
        }
        if (!Files.exists(marker)) throw new IllegalStateException("deterministic provider did not persist its external effect marker");
        System.out.println("FIRST_PROCESS_CHECKPOINTED " + value(interrupted, "operationId"));
    }

    private static void runReplacementProcess(DataSource dataSource, String scope, String execution,
                                              String threadId, Path marker, Instant baseTime) throws Exception {
        var host = new ProcessHost(dataSource, scope, execution, marker, baseTime);
        var builder = graph(host);
        PostgresSaver saver = PostgresLangGraph4jCheckpointSaverFactory.create(dataSource, builder);
        var compiled = builder.compile(CompileConfig.builder().checkpointSaver(saver)
                .interruptBefore("await-approval", "reconcile").releaseThread(false).recursionLimit(24).build());
        var config = RunnableConfig.builder().threadId(threadId).build();
        var restored = compiled.getState(config).state();
        if (!value(restored, "actionStatus").equals(ActionLedgerClaim.Status.UNKNOWN_OUTCOME.name())) {
            throw new IllegalStateException("replacement JVM did not restore the ambiguous action state");
        }
        host.verifyRestoredDurableState(restored);

        var terminal = compiled.invokeFinal(GraphInput.resume(), config)
                .orElseThrow(() -> new IllegalStateException("replacement JVM returned no terminal workflow result"));
        if (!END.equals(terminal.node()) || !"COMPLETED".equals(value(terminal.state(), "status"))) {
            throw new IllegalStateException("replacement JVM did not complete after authoritative reconciliation");
        }
        if (!"ALREADY_COMPLETED".equals(value(terminal.state(), "replayStatus"))) {
            throw new IllegalStateException("reconciled operation did not suppress the duplicate dispatch");
        }
        var finalBudget = host.budgets.snapshot(execution, baseTime.plusSeconds(4));
        if (amount(finalBudget.consumed(), ResourceUnit.MODEL_CALLS) != 2L
                || amount(finalBudget.reserved(), ResourceUnit.MODEL_CALLS) != 0L) {
            throw new IllegalStateException("replacement JVM did not settle the durable B6 reservation exactly once");
        }
        System.out.println("REPLACEMENT_PROCESS_RECONCILED " + value(terminal.state(), "operationId"));
    }

    private static StateGraph<ProbeState> graph(ProcessHost host) throws Exception {
        return new StateGraph<>(ProbeState.SCHEMA, ProbeState::new)
                .addNode("prepare", node_async(state -> host.prepare(state)))
                .addNode("await-approval", node_async(state -> host.approveAndDispatch(state)))
                .addNode("reconcile", node_async(state -> host.reconcile(state)))
                .addNode("finish", node_async(state -> Map.of("status", "COMPLETED")))
                .addEdge(START, "prepare")
                .addEdge("prepare", "await-approval")
                .addEdge("await-approval", "reconcile")
                .addEdge("reconcile", "finish")
                .addEdge("finish", END);
    }

    private static final class ProcessHost {
        private final String scope;
        private final String execution;
        private final Path marker;
        private final Instant baseTime;
        private final PostgresRecoveryIntentionRepository intentionRepository;
        private final RecoveryIntentionService intentions;
        private final PostgresApprovalStore approvals;
        private final PostgresResourceBudgetLedger budgets;
        private final PostgresActionExecutionLedger ledger;
        private final ActionHandler<String, String> handler;
        private final ActionDispatcher dispatcher;
        private final AtomicInteger handlerCalls = new AtomicInteger();
        private final RecoveryIntentionId intentionId;
        private final String approvalId;

        private ProcessHost(DataSource dataSource, String scope, String execution, Path marker, Instant baseTime) {
            this.scope = scope;
            this.execution = execution;
            this.marker = marker;
            this.baseTime = baseTime;
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
            this.handler = handler(marker, handlerCalls);
            this.dispatcher = new ActionDispatcher(new ActionRegistry().register(handler), new CapabilityAuthorizer(), ledger);
        }

        Map<String, Object> prepare(ProbeState state) {
            if (!scope.equals(value(state, "scopeId")) || !execution.equals(value(state, "executionId"))) {
                throw new SecurityException("forked workflow input crossed execution scope");
            }
            RecoveryObjective objective = new RecoveryObjective(new RecoveryObjectiveId("objective:" + execution),
                    "Restore affected order fulfilment", Set.of(new CustomerOrderId("order:" + execution)),
                    baseTime.plusSeconds(1800), Set.of(RecoveryConstraint.NO_OVER_ALLOCATION,
                    RecoveryConstraint.APPROVAL_REQUIRED_FOR_EXTERNAL_COMMITMENT), RecoveryObjectiveStatus.ACTIVE, 0);
            RecoveryIntention intention = intentions.create(intentionId, objective, new MemoryScopeId(scope),
                    "m8b4d-planner", new RecoveryMethodReference("mcts", "candidate:" + execution), baseTime);
            var binding = binding(intention.version());
            approvals.create(scope, approvalId, binding, "m8b4d-requester",
                    "{\"capabilities\":[\"INVENTORY_RESERVE\"]}",
                    "{\"warehouse\":\"WH-M8B4D\"}", "evidence:inventory-v1", "approval-policy-v1",
                    baseTime.plusSeconds(900), baseTime);
            budgets.register(execution, new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 8L), baseTime.plusSeconds(1800)));
            budgets.reserve(execution, "planning-reservation", ResourceUsage.of(ResourceUnit.MODEL_CALLS, 2), baseTime);
            return Map.of("intentionId", intention.id().value(), "intentionVersion", Long.toString(intention.version()),
                    "objectiveFingerprint", intention.objectiveFingerprint(), "approvalId", approvalId,
                    "requestId", binding.requestId(), "candidateSetId", binding.candidateSetId(),
                    "candidateId", binding.candidateId(), "planFingerprint", binding.actionPlanFingerprint(),
                    "status", "WAITING_FOR_APPROVAL");
        }

        Map<String, Object> approveAndDispatch(ProbeState state) {
            ApprovalRecord approved = approvals.find(scope, value(state, "approvalId"))
                    .orElseThrow(() -> new SecurityException("persisted approval disappeared after process pause"));
            if (approved.status() != ApprovalRecord.Status.APPROVED
                    || !"m8b4d-response".equals(approved.responseId())
                    || !approvals.isCurrentlyValid(scope, approved.approvalId(), bindingFrom(state), baseTime.plusSeconds(2))) {
                throw new SecurityException("replacement action is not covered by the exact persisted approval");
            }
            RecoveryIntention intention = intentions.get(new RecoveryIntentionId(value(state, "intentionId")));
            if (intention.version() != Long.parseLong(value(state, "intentionVersion"))
                    || !intention.objectiveFingerprint().equals(value(state, "objectiveFingerprint"))) {
                throw new SecurityException("protected intention changed before dispatch");
            }
            var pendingBudget = budgets.snapshot(execution, baseTime.plusSeconds(2));
            if (amount(pendingBudget.reserved(), ResourceUnit.MODEL_CALLS) != 2L) {
                throw new IllegalStateException("B6 budget reservation was not restored before dispatch");
            }
            ActionResult<String> result = dispatcher.dispatch(ACTION_ID, String.class, String.class, actionContext(), INPUT);
            if (result.status() != ActionStatus.FAILED
                    || !result.failureOptional().orElseThrow().outcomeUnknown()) {
                throw new IllegalStateException("provider failure did not persist UNKNOWN_OUTCOME");
            }
            ActionLedgerClaim claim = ledger.inspect(ledgerKey(), requestFingerprint()).orElseThrow();
            return Map.of("actionStatus", claim.status().name(), "operationId", claim.operationId());
        }

        Map<String, Object> reconcile(ProbeState state) {
            ActionLedgerClaim unknown = ledger.inspect(ledgerKey(), requestFingerprint()).orElseThrow();
            if (unknown.status() != ActionLedgerClaim.Status.UNKNOWN_OUTCOME) {
                throw new IllegalStateException("reconciliation did not find the persisted unknown operation");
            }
            ActionResult<String> blocked = dispatcher.dispatch(ACTION_ID, String.class, String.class, actionContext(), INPUT);
            if (blocked.status() != ActionStatus.FAILED || !blocked.failureOptional().orElseThrow().outcomeUnknown()
                    || handlerCalls.get() != 0) {
                throw new SecurityException("unknown outcome was blindly redispatched in the replacement JVM");
            }
            String external;
            try {
                external = Files.readString(marker, StandardCharsets.UTF_8);
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("external provider evidence could not be read", failure);
            }
            String[] values = external.split("\\R", 2);
            if (values.length != 2 || !values[0].equals(unknown.operationId()) || !values[1].equals(INPUT)) {
                throw new SecurityException("provider evidence did not match the persisted M3 operation");
            }
            ActionReceipt receipt = new ActionReceipt(ACTION_ID, unknown.operationId(), PRINCIPAL_ID, CORRELATION_ID,
                    baseTime.plusSeconds(1), baseTime.plusSeconds(3), "provider://m8b4d/" + IDEMPOTENCY_KEY,
                    Map.of("reconciled", "true", "source", "authoritative-provider-read"));
            ledger.reconcileUnknownAsCompleted(ledgerKey(), receipt);
            ActionResult<String> replay = dispatcher.dispatch(ACTION_ID, String.class, String.class, actionContext(), INPUT);
            if (replay.status() != ActionStatus.ALREADY_COMPLETED || handlerCalls.get() != 0) {
                throw new IllegalStateException("completed replay re-executed the external action");
            }
            budgets.commit(execution, "planning-reservation", ResourceUsage.of(ResourceUnit.MODEL_CALLS, 2),
                    baseTime.plusSeconds(3));
            return Map.of("workflowOutcome", "RECONCILED", "replayStatus", replay.status().name(),
                    "operationId", unknown.operationId());
        }

        void verifyRestoredDurableState(ProbeState state) {
            RecoveryIntention restored = intentions.get(new RecoveryIntentionId(value(state, "intentionId")));
            if (restored.version() != Long.parseLong(value(state, "intentionVersion"))
                    || !restored.objectiveFingerprint().equals(value(state, "objectiveFingerprint"))) {
                throw new SecurityException("independent JVM did not restore the same protected intention version/objective");
            }
            if (!approvals.isCurrentlyValid(scope, value(state, "approvalId"), bindingFrom(state), baseTime.plusSeconds(2))) {
                throw new SecurityException("independent JVM did not restore the exact approved authority binding");
            }
            var pending = budgets.snapshot(execution, baseTime.plusSeconds(2));
            if (amount(pending.reserved(), ResourceUnit.MODEL_CALLS) != 2L) {
                throw new IllegalStateException("independent JVM did not restore the active B6 reservation");
            }
            var claim = ledger.inspect(ledgerKey(), requestFingerprint()).orElseThrow();
            if (claim.status() != ActionLedgerClaim.Status.UNKNOWN_OUTCOME) {
                throw new IllegalStateException("independent JVM did not restore the M3 unknown-outcome record");
            }
        }

        private CognitiveCycleAuthorityPort.ApprovalBinding binding(long version) {
            return new CognitiveCycleAuthorityPort.ApprovalBinding("request:" + execution, execution, scope,
                    "candidate-set:" + execution, "candidate:" + execution, intentionId.value(), version, "a".repeat(64));
        }
        private CognitiveCycleAuthorityPort.ApprovalBinding bindingFrom(ProbeState state) {
            return binding(Long.parseLong(value(state, "intentionVersion")));
        }
        private IdempotencyLedgerKey ledgerKey() { return new IdempotencyLedgerKey(PRINCIPAL_ID, ACTION_ID, IDEMPOTENCY_KEY); }
        private String requestFingerprint() { return sha256(handler.requestFingerprint(INPUT)); }
        private ActionContext actionContext() {
            return ActionContext.create(execution, CORRELATION_ID, principal(), baseTime.plusSeconds(2), IDEMPOTENCY_KEY);
        }
    }

    private static ActionHandler<String, String> handler(Path marker, AtomicInteger calls) {
        ActionDescriptor descriptor = new ActionDescriptor(ACTION_ID, "M8-B4-D process-boundary test write",
                "Deterministic provider that commits once then loses its response", dev.supplychain.agent.actions.api.ActionCategory.GROUNDING,
                ActionEffect.DOMAIN_WRITE, Capability.INVENTORY_RESERVE, true);
        return new ActionHandler<>() {
            @Override public ActionDescriptor descriptor() { return descriptor; }
            @Override public Class<String> inputType() { return String.class; }
            @Override public Class<String> outputType() { return String.class; }
            @Override public Set<ResourceReference> resources(String input) { return Set.of(new ResourceReference("warehouse", "WH-M8B4D")); }
            @Override public String execute(ActionContext context, String input) {
                calls.incrementAndGet();
                try {
                    Files.writeString(marker, context.operationIdOptional().orElseThrow() + "\n" + input,
                            StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                } catch (Exception ex) {
                    throw new IllegalStateException("provider effect already exists or cannot be persisted", ex);
                }
                throw new IllegalStateException("provider committed but the response timed out");
            }
            @Override public String requestFingerprint(String input) { return "m8b4d-write:" + input; }
        };
    }

    private static ActionPrincipal principal() {
        return new ActionPrincipal(PRINCIPAL_ID, Set.of(CapabilityGrant.scoped(Capability.INVENTORY_RESERVE,
                Set.of(new ResourceReference("warehouse", "WH-M8B4D")))));
    }

    private static long amount(Map<ResourceUnit, Long> values, ResourceUnit unit) {
        return values.getOrDefault(unit, 0L);
    }

    private static String value(AgentState state, String key) {
        return state.value(key).map(Object::toString).orElse("");
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static final class ProbeState extends AgentState {
        private static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
                Map.entry("scopeId", Channels.base(() -> "")),
                Map.entry("executionId", Channels.base(() -> "")),
                Map.entry("intentionId", Channels.base(() -> "")),
                Map.entry("intentionVersion", Channels.base(() -> "0")),
                Map.entry("objectiveFingerprint", Channels.base(() -> "")),
                Map.entry("approvalId", Channels.base(() -> "")),
                Map.entry("requestId", Channels.base(() -> "")),
                Map.entry("candidateSetId", Channels.base(() -> "")),
                Map.entry("candidateId", Channels.base(() -> "")),
                Map.entry("planFingerprint", Channels.base(() -> "")),
                Map.entry("approvalResponseId", Channels.base(() -> "")),
                Map.entry("actionStatus", Channels.base(() -> "")),
                Map.entry("operationId", Channels.base(() -> "")),
                Map.entry("workflowOutcome", Channels.base(() -> "")),
                Map.entry("replayStatus", Channels.base(() -> "")),
                Map.entry("status", Channels.base(() -> "CREATED")));
        private ProbeState(Map<String, Object> initialData) { super(initialData); }
    }
}
