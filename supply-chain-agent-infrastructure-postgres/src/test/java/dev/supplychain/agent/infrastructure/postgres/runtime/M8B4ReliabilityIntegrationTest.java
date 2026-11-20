package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.actions.api.ActionCategory;
import dev.supplychain.agent.actions.api.ActionFailure;
import dev.supplychain.agent.actions.api.ActionFailureCode;
import dev.supplychain.agent.actions.api.ActionContext;
import dev.supplychain.agent.actions.api.ActionStatus;
import dev.supplychain.agent.actions.api.ActionDescriptor;
import dev.supplychain.agent.actions.api.ActionDispatcher;
import dev.supplychain.agent.actions.api.ActionEffect;
import dev.supplychain.agent.actions.api.ActionHandler;
import dev.supplychain.agent.actions.api.ActionId;
import dev.supplychain.agent.actions.api.ActionPrincipal;
import dev.supplychain.agent.actions.api.ActionReceipt;
import dev.supplychain.agent.actions.api.ActionRegistry;
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
import dev.supplychain.agent.infrastructure.postgres.config.PostgresDataSourceFactory;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresDatabaseConfig;
import dev.supplychain.agent.infrastructure.postgres.migration.PostgresMigrationRunner;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleAuthorityPort;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.mechanisms.b4.IntentionStatus;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntention;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntentionId;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntentionService;
import dev.supplychain.agent.mechanisms.b4.RecoveryMethodReference;
import dev.supplychain.agent.mechanisms.b6.BudgetReservation;
import dev.supplychain.agent.mechanisms.b6.ReservationStatus;
import dev.supplychain.agent.mechanisms.b6.ResourceBudget;
import dev.supplychain.agent.mechanisms.b6.ResourceBudgetExceededException;
import dev.supplychain.agent.mechanisms.b6.ResourceUnit;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.EnumSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** M8-B4 integration checks against the production PostgreSQL repositories and M3 dispatcher. */
@EnabledIfSystemProperty(named = "phase0.postgres", matches = "true")
class M8B4ReliabilityIntegrationTest {
    private static final Instant NOW = Instant.now().minusSeconds(5).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    private static DataSource dataSource;

    @BeforeAll static void initializeSchema() {
        dataSource = PostgresDataSourceFactory.create(PostgresDatabaseConfig.fromEnvironment(System.getenv()));
        assertEquals(9, new PostgresMigrationRunner(dataSource).migrate().totalMigrations());
    }

    @Test void b6BudgetReservationsAndActualUsageSurviveRepositoryReplacement() {
        String scope = "m8b4-budget-restart-" + UUID.randomUUID();
        String execution = "execution-" + UUID.randomUUID();
        var firstHost = new PostgresResourceBudgetLedger(dataSource, scope);
        var budget = new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 10L,
                ResourceUnit.SEARCH_EXPANSIONS, 8L), NOW.plusSeconds(120));
        firstHost.register(execution, budget);
        ResourceUsage estimate = new ResourceUsage(Map.of(ResourceUnit.MODEL_CALLS, 5L,
                ResourceUnit.SEARCH_EXPANSIONS, 3L));
        BudgetReservation reserved = firstHost.reserve(execution, "planner-step-1", estimate, NOW);
        assertEquals(ReservationStatus.ACTIVE, reserved.status());

        // A replacement repository instance represents a new application process.
        var replacementHost = new PostgresResourceBudgetLedger(dataSource, scope);
        ResourceBudgetSnapshotWhileRestart snapshot = ResourceBudgetSnapshotWhileRestart.from(
                replacementHost.snapshot(execution, NOW.plusSeconds(1)));
        assertEquals(5L, snapshot.remainingModelCalls());
        assertEquals(3L, snapshot.reservedSearchExpansions());

        ResourceUsage actual = new ResourceUsage(Map.of(ResourceUnit.MODEL_CALLS, 4L,
                ResourceUnit.SEARCH_EXPANSIONS, 2L));
        var committed = replacementHost.commit(execution, "planner-step-1", actual, NOW.plusSeconds(2));
        assertEquals(4L, committed.consumed().get(ResourceUnit.MODEL_CALLS));
        assertEquals(2L, committed.consumed().get(ResourceUnit.SEARCH_EXPANSIONS));
        assertEquals(6L, committed.remaining(ResourceUnit.MODEL_CALLS));
        var restoredConsumption = new PostgresResourceBudgetLedger(dataSource, scope)
                .snapshot(execution, NOW.plusSeconds(3)).consumed();
        assertEquals(4L, restoredConsumption.get(ResourceUnit.MODEL_CALLS));
        assertEquals(2L, restoredConsumption.get(ResourceUnit.SEARCH_EXPANSIONS));
        assertEquals(committed.consumed(), replacementHost.commit(execution, "planner-step-1", actual,
                NOW.plusSeconds(4)).consumed(), "replaying the same commit must not double-charge usage");
        assertThrows(IllegalStateException.class, () -> replacementHost.commit(execution, "planner-step-1",
                ResourceUsage.of(ResourceUnit.MODEL_CALLS, 1), NOW.plusSeconds(5)));
    }

    @Test void budgetAccountInitializationIsIdempotentButCannotRebindLimits() {
        String scope = "m8b4-budget-init-" + UUID.randomUUID();
        String execution = "execution-" + UUID.randomUUID();
        var ledger = new PostgresResourceBudgetLedger(dataSource, scope);
        var budget = new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 3L,
                ResourceUnit.REASONING_EVALUATIONS, 5L), NOW.plusSeconds(120));

        ledger.ensureRegistered(execution, budget);
        new PostgresResourceBudgetLedger(dataSource, scope).ensureRegistered(execution, budget);

        assertEquals(budget, ledger.snapshot(execution, NOW).budget(),
                "repeated host startup may verify but must not replace the registered account");
        var changed = new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 30L,
                ResourceUnit.REASONING_EVALUATIONS, 50L), NOW.plusSeconds(120));
        assertThrows(SecurityException.class,
                () -> new PostgresResourceBudgetLedger(dataSource, scope).ensureRegistered(execution, changed),
                "a resumed execution cannot silently receive a larger budget");
    }

    @Test void independentBudgetLedgerInstancesCannotOversubscribeOneExecution() throws Exception {
        String scope = "m8b4-budget-concurrency-" + UUID.randomUUID();
        String execution = "execution-" + UUID.randomUUID();
        var first = new PostgresResourceBudgetLedger(dataSource, scope);
        var second = new PostgresResourceBudgetLedger(dataSource, scope);
        first.register(execution, new ResourceBudget(Map.of(ResourceUnit.MCTS_ROLLOUTS, 10L), NOW.plusSeconds(120)));
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> reserveAfter(start, first, execution, "worker-a"));
            var b = pool.submit(() -> reserveAfter(start, second, execution, "worker-b"));
            start.countDown();
            boolean firstWon = a.get(10, TimeUnit.SECONDS);
            boolean secondWon = b.get(10, TimeUnit.SECONDS);
            assertTrue(firstWon ^ secondWon, "exactly one 7-unit reservation may fit in a 10-unit budget");
        }
        var snapshot = new PostgresResourceBudgetLedger(dataSource, scope).snapshot(execution, NOW.plusSeconds(1));
        assertEquals(7L, snapshot.reserved().get(ResourceUnit.MCTS_ROLLOUTS));
        assertEquals(3L, snapshot.remaining(ResourceUnit.MCTS_ROLLOUTS));
    }

    @Test void actualUsageOverrunRemainsVisibleAndBlocksFurtherReservations() {
        String scope = "m8b4-budget-overrun-" + UUID.randomUUID();
        String execution = "execution-" + UUID.randomUUID();
        var firstHost = new PostgresResourceBudgetLedger(dataSource, scope);
        firstHost.register(execution, new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 4L), NOW.plusSeconds(60)));
        firstHost.reserve(execution, "underestimated-step", ResourceUsage.of(ResourceUnit.MODEL_CALLS, 2), NOW);

        var restartedHost = new PostgresResourceBudgetLedger(dataSource, scope);
        var overrun = restartedHost.commit(execution, "underestimated-step",
                ResourceUsage.of(ResourceUnit.MODEL_CALLS, 5), NOW.plusSeconds(1));
        assertEquals(5L, overrun.consumed().get(ResourceUnit.MODEL_CALLS));
        assertTrue(overrun.overBudget(), "actual usage above the configured limit must remain visible after restart");
        assertThrows(ResourceBudgetExceededException.class,
                () -> new PostgresResourceBudgetLedger(dataSource, scope).reserve(execution, "must-stop",
                        ResourceUsage.of(ResourceUnit.MODEL_CALLS, 1), NOW.plusSeconds(2)));
    }

    @Test void releasedReservationCannotBeReusedAndDeadlineIsEnforcedAfterRestart() {
        String scope = "m8b4-budget-lifecycle-" + UUID.randomUUID();
        String execution = "execution-" + UUID.randomUUID();
        var ledger = new PostgresResourceBudgetLedger(dataSource, scope);
        ledger.register(execution, new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 4L), NOW.plusSeconds(10)));
        ResourceUsage requested = ResourceUsage.of(ResourceUnit.MODEL_CALLS, 2);
        ledger.reserve(execution, "reservation", requested, NOW);
        var restarted = new PostgresResourceBudgetLedger(dataSource, scope);
        assertEquals(4L, restarted.release(execution, "reservation", NOW.plusSeconds(1))
                .remaining(ResourceUnit.MODEL_CALLS),
                "releasing a held reservation must restore its two units to the available budget");
        assertThrows(IllegalStateException.class,
                () -> restarted.reserve(execution, "reservation", requested, NOW.plusSeconds(2)));
        assertThrows(ResourceBudgetExceededException.class,
                () -> restarted.reserve(execution, "late-reservation", requested, NOW.plusSeconds(10)));
    }

    @Test void persistentIntentionResumesAfterRepositoryAndServiceReplacementWithoutObjectiveDrift() {
        String scope = "m8b4-intention-restart-" + UUID.randomUUID();
        RecoveryObjective objective = new RecoveryObjective(new RecoveryObjectiveId("objective-" + UUID.randomUUID()),
                "Restore affected customer orders", Set.of(new CustomerOrderId("order-" + UUID.randomUUID())),
                NOW.plusSeconds(3600), Set.of(RecoveryConstraint.NO_OVER_ALLOCATION,
                RecoveryConstraint.APPROVAL_REQUIRED_FOR_EXTERNAL_COMMITMENT), RecoveryObjectiveStatus.ACTIVE, 0);
        RecoveryIntentionId id = RecoveryIntentionId.random();
        var intentionCodec = recoveryIntentionCodec();
        var firstRepository = new PostgresRecoveryIntentionRepository(dataSource, scope, intentionCodec);
        var firstService = new RecoveryIntentionService(firstRepository);
        RecoveryIntention created = firstService.create(id, objective, new MemoryScopeId(scope), "planner-owner",
                new RecoveryMethodReference("cot", "plan:cot"), NOW);
        RecoveryIntention suspended = firstService.suspend(id, created.version(), "awaiting supplier confirmation", NOW.plusSeconds(1));

        // A new stateless codec instance proves the JSONB payload is sufficient after process restart.
        var replacementRepository = new PostgresRecoveryIntentionRepository(dataSource, scope, recoveryIntentionCodec());
        var replacementService = new RecoveryIntentionService(replacementRepository);
        RecoveryIntention restored = replacementService.get(id);
        assertEquals(suspended, restored);
        assertEquals(created.objectiveFingerprint(), restored.objectiveFingerprint());
        assertEquals(created.protectedConstraints(), restored.protectedConstraints());
        assertEquals("cot", restored.currentMethod().methodId());

        RecoveryIntention resumed = replacementService.resume(id, restored.version(), NOW.plusSeconds(2));
        assertEquals(dev.supplychain.agent.mechanisms.b4.IntentionStatus.ACTIVE, resumed.status());
        RecoveryIntention pending = replacementService.requestReconsideration(id, resumed.version(),
                "supplier capacity changed", NOW.plusSeconds(3));
        RecoveryIntention changedMethod = replacementService.changeMethod(id, pending.version(),
                new RecoveryMethodReference("mcts", "plan:mcts"), created.objectiveFingerprint(), NOW.plusSeconds(4));
        assertEquals("mcts", changedMethod.currentMethod().methodId());
        assertEquals(created.objectiveFingerprint(), changedMethod.objectiveFingerprint());
        assertEquals(created.protectedConstraints(), changedMethod.protectedConstraints());
    }

    @Test void approvalBindingRevocationAndExpiryRemainAuthoritativeAfterHostReplacement() {
        String scope = "m8b4-approval-restart-" + UUID.randomUUID();
        String approvalId = "approval-" + UUID.randomUUID();
        var binding = new CognitiveCycleAuthorityPort.ApprovalBinding("request-" + UUID.randomUUID(),
                "execution-" + UUID.randomUUID(), scope, "candidate-set-1", "candidate-1",
                "intention-1", 7, "a".repeat(64));
        var firstHost = new PostgresApprovalStore(dataSource, scope);
        var pending = firstHost.create(scope, approvalId, binding, "requester-1",
                "{\"capabilities\":[\"inventory-reserve\"]}",
                "{\"warehouse\":\"WH-M8B4\"}", "evidence-set-1", "approval-policy-v1",
                NOW.plusSeconds(60), NOW);
        var approved = firstHost.decide(scope, approvalId, pending.revision(), true,
                "reviewer-1", "response-1", "approved", NOW.plusSeconds(1));

        // A replacement authority host must re-read the decision rather than trust graph state.
        var replacementHost = new PostgresApprovalStore(dataSource, scope);
        assertTrue(replacementHost.isCurrentlyValid(scope, approvalId, binding, NOW.plusSeconds(2)));
        var changedIntention = new CognitiveCycleAuthorityPort.ApprovalBinding(binding.requestId(),
                binding.executionId(), scope, binding.candidateSetId(), binding.candidateId(),
                binding.intentionId(), binding.intentionVersion() + 1, binding.actionPlanFingerprint());
        assertFalse(replacementHost.isCurrentlyValid(scope, approvalId, changedIntention, NOW.plusSeconds(2)),
                "an approval for an older intention version cannot authorize the resumed plan");
        assertTrue(replacementHost.revoke(scope, approvalId, approved.revision(), "security-reviewer",
                "revoked during interruption", NOW.plusSeconds(3)));
        assertFalse(new PostgresApprovalStore(dataSource, scope).isCurrentlyValid(scope, approvalId, binding,
                NOW.plusSeconds(4)), "revoked approval must fail closed after process restart");

        String expiringId = "approval-expiring-" + UUID.randomUUID();
        replacementHost.create(scope, expiringId, binding, "requester-1",
                "{\"capabilities\":[\"inventory-reserve\"]}",
                "{\"warehouse\":\"WH-M8B4\"}", "evidence-set-1", "approval-policy-v1",
                NOW.plusSeconds(5), NOW);
        assertFalse(replacementHost.isCurrentlyValid(scope, expiringId, binding, NOW.plusSeconds(6)));
        assertEquals(ApprovalRecord.Status.EXPIRED, replacementHost.find(scope, expiringId).orElseThrow().status());
    }

    @Test void ambiguousExternalWriteIsNotRedispatchedAfterDispatcherReplacement() throws Exception {
        String scope = "m8b4-unknown-effect-" + UUID.randomUUID();
        AtomicInteger externalEffects = new AtomicInteger();
        ActionId actionId = new ActionId("m8b4.test.inventory.reserve");
        ActionDescriptor descriptor = new ActionDescriptor(actionId, "Reserve inventory",
                "M8-B4 fault-injection handler", ActionCategory.GROUNDING, ActionEffect.DOMAIN_WRITE,
                Capability.INVENTORY_RESERVE, true);
        ActionHandler<String, String> handler = new ActionHandler<>() {
            @Override public ActionDescriptor descriptor() { return descriptor; }
            @Override public Class<String> inputType() { return String.class; }
            @Override public Class<String> outputType() { return String.class; }
            @Override public Set<ResourceReference> resources(String input) {
                return Set.of(new ResourceReference("warehouse", "WH-M8B4"));
            }
            @Override public String execute(ActionContext context, String input) {
                externalEffects.incrementAndGet();
                throw new IllegalStateException("provider timed out after a possible commit");
            }
            @Override public String requestFingerprint(String input) { return "reserve:" + input; }
        };
        ActionRegistry registry = new ActionRegistry().register(handler);
        ActionPrincipal principal = new ActionPrincipal("operator-m8b4",
                Set.of(CapabilityGrant.scoped(Capability.INVENTORY_RESERVE,
                        Set.of(new ResourceReference("warehouse", "WH-M8B4")))));
        String executionId = "execution-" + UUID.randomUUID();
        ActionContext context = ActionContext.create(executionId, "correlation-m8b4",
                principal, NOW, "reserve-key-" + UUID.randomUUID());
        var firstLedger = new PostgresActionExecutionLedger(dataSource, scope, actionReceiptCodec(), actionFailureCodec(), Duration.ofSeconds(30));
        var firstDispatcher = new ActionDispatcher(registry, new CapabilityAuthorizer(), firstLedger);

        var first = firstDispatcher.dispatch(actionId, String.class, String.class, context, "LOT-1:2");
        assertEquals(ActionStatus.FAILED, first.status());
        assertTrue(first.failureOptional().orElseThrow().outcomeUnknown());
        assertEquals(1, externalEffects.get());

        // A new dispatcher/ledger instance simulates a restarted application process.
        var replacementLedger = new PostgresActionExecutionLedger(dataSource, scope, actionReceiptCodec(), actionFailureCodec(), Duration.ofSeconds(30));
        var replacementDispatcher = new ActionDispatcher(registry, new CapabilityAuthorizer(), replacementLedger);
        var replay = replacementDispatcher.dispatch(actionId, String.class, String.class, context, "LOT-1:2");
        assertEquals(ActionStatus.FAILED, replay.status());
        assertTrue(replay.failureOptional().orElseThrow().outcomeUnknown());
        assertEquals(1, externalEffects.get(), "unknown outcomes must not be blindly redispatched after restart");

        IdempotencyLedgerKey key = new IdempotencyLedgerKey(principal.principalId(), actionId, context.idempotencyKey());
        String fingerprint = sha256(registry.requestFingerprint(actionId, "LOT-1:2"));
        var unknown = replacementLedger.inspect(key, fingerprint).orElseThrow();
        ActionReceipt reconciled = new ActionReceipt(actionId, unknown.operationId(), principal.principalId(),
                context.correlationId(), NOW, NOW.plusSeconds(1), "external://reservation/confirmed", Map.of("reconciled", "true"));
        replacementLedger.reconcileUnknownAsCompleted(key, reconciled);
        var acknowledged = replacementDispatcher.dispatch(actionId, String.class, String.class, context, "LOT-1:2");
        assertEquals(ActionStatus.ALREADY_COMPLETED, acknowledged.status());
        assertEquals(1, externalEffects.get(), "reconciled duplicate must return the existing receipt without repeating the effect");
    }

    private static boolean reserveAfter(CountDownLatch start, PostgresResourceBudgetLedger ledger,
                                        String execution, String reservation) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try {
            ledger.reserve(execution, reservation, ResourceUsage.of(ResourceUnit.MCTS_ROLLOUTS, 7), NOW);
            return true;
        } catch (ResourceBudgetExceededException expected) {
            return false;
        }
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    static JsonCodec<RecoveryIntention> recoveryIntentionCodec() {
        return new JsonCodec<>() {
            @Override public String encode(RecoveryIntention value) {
                String constraints = value.protectedConstraints().stream().map(Enum::name).sorted()
                        .map(M8B4ReliabilityIntegrationTest::jsonQuote).collect(Collectors.joining(","));
                return "{"
                        + "\"id\":" + jsonQuote(value.id().value()) + ","
                        + "\"scopeId\":" + jsonQuote(value.scopeId().value()) + ","
                        + "\"objectiveId\":" + jsonQuote(value.objectiveId().value()) + ","
                        + "\"objectiveFingerprint\":" + jsonQuote(value.objectiveFingerprint()) + ","
                        + "\"objectiveOwner\":" + jsonQuote(value.objectiveOwner()) + ","
                        + "\"protectedConstraints\":[" + constraints + "],"
                        + "\"methodId\":" + jsonQuote(value.currentMethod().methodId()) + ","
                        + "\"candidatePlanReference\":" + jsonQuote(value.currentMethod().candidatePlanReference()) + ","
                        + "\"status\":" + jsonQuote(value.status().name()) + ","
                        + "\"version\":" + value.version() + ","
                        + "\"createdAt\":" + jsonQuote(value.createdAt().toString()) + ","
                        + "\"updatedAt\":" + jsonQuote(value.updatedAt().toString()) + ","
                        + "\"reason\":" + jsonNullableQuote(value.reason()) + ","
                        + "\"resumeStatus\":" + jsonNullableQuote(value.resumeStatus() == null ? null : value.resumeStatus().name()) + ","
                        + "\"delegationId\":" + jsonNullableQuote(value.delegationId()) + ","
                        + "\"completionProof\":null}";
            }

            @Override public RecoveryIntention decode(String json) {
                String proof = jsonNullableString(json, "completionProof");
                if (proof != null) throw new IllegalStateException("test codec does not model completion proofs");
                EnumSet<RecoveryConstraint> constraints = EnumSet.noneOf(RecoveryConstraint.class);
                Matcher array = Pattern.compile("\\\"protectedConstraints\\\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(json);
                if (!array.find()) throw new IllegalArgumentException("protectedConstraints payload missing");
                Matcher items = Pattern.compile("\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"").matcher(array.group(1));
                while (items.find()) constraints.add(RecoveryConstraint.valueOf(jsonUnescape(items.group(1))));
                String resume = jsonNullableString(json, "resumeStatus");
                return new RecoveryIntention(
                        new RecoveryIntentionId(jsonString(json, "id")),
                        new MemoryScopeId(jsonString(json, "scopeId")),
                        new RecoveryObjectiveId(jsonString(json, "objectiveId")),
                        jsonString(json, "objectiveFingerprint"),
                        jsonString(json, "objectiveOwner"), constraints,
                        new RecoveryMethodReference(jsonString(json, "methodId"), jsonString(json, "candidatePlanReference")),
                        IntentionStatus.valueOf(jsonString(json, "status")),
                        jsonLong(json, "version"), Instant.parse(jsonString(json, "createdAt")),
                        Instant.parse(jsonString(json, "updatedAt")), jsonNullableString(json, "reason"),
                        resume == null ? null : IntentionStatus.valueOf(resume),
                        jsonNullableString(json, "delegationId"), null);
            }
        };
    }

    static JsonCodec<ActionFailure> actionFailureCodec() {
        return new JsonCodec<>() {
            @Override public String encode(ActionFailure value) {
                return "{\"code\":" + jsonQuote(value.code().name())
                        + ",\"message\":" + jsonQuote(value.message())
                        + ",\"retryable\":" + value.retryable()
                        + ",\"outcomeUnknown\":" + value.outcomeUnknown() + "}";
            }
            @Override public ActionFailure decode(String json) {
                return new ActionFailure(ActionFailureCode.valueOf(jsonString(json, "code")),
                        jsonString(json, "message"), jsonBoolean(json, "retryable"),
                        jsonBoolean(json, "outcomeUnknown"));
            }
        };
    }

    static JsonCodec<ActionReceipt> actionReceiptCodec() {
        return new JsonCodec<>() {
            @Override public String encode(ActionReceipt value) {
                String attributes = value.attributes().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> jsonQuote(entry.getKey()) + ":" + jsonQuote(entry.getValue()))
                        .collect(Collectors.joining(","));
                return "{\"actionId\":" + jsonQuote(value.actionId().value())
                        + ",\"operationId\":" + jsonQuote(value.operationId())
                        + ",\"principalId\":" + jsonQuote(value.principalId())
                        + ",\"correlationId\":" + jsonQuote(value.correlationId())
                        + ",\"startedAt\":" + jsonQuote(value.startedAt().toString())
                        + ",\"completedAt\":" + jsonQuote(value.completedAt().toString())
                        + ",\"resultReference\":" + jsonNullableQuote(value.resultReference())
                        + ",\"attributes\":{" + attributes + "}}";
            }
            @Override public ActionReceipt decode(String json) {
                Map<String, String> attributes = new java.util.LinkedHashMap<>();
                Matcher object = Pattern.compile("\\\"attributes\\\"\\s*:\\s*\\{(.*?)\\}", Pattern.DOTALL).matcher(json);
                if (object.find()) {
                    Matcher pairs = Pattern.compile("\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"").matcher(object.group(1));
                    while (pairs.find()) attributes.put(jsonUnescape(pairs.group(1)), jsonUnescape(pairs.group(2)));
                }
                return new ActionReceipt(new ActionId(jsonString(json, "actionId")),
                        jsonString(json, "operationId"), jsonString(json, "principalId"),
                        jsonString(json, "correlationId"), Instant.parse(jsonString(json, "startedAt")),
                        Instant.parse(jsonString(json, "completedAt")), jsonNullableString(json, "resultReference"), attributes);
            }
        };
    }

    private static String jsonString(String json, String name) {
        String value = jsonNullableString(json, name);
        if (value == null) throw new IllegalArgumentException("JSON string field missing or null: " + name);
        return value;
    }

    private static String jsonNullableString(String json, String name) {
        Pattern field = Pattern.compile("\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*(?:\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"|null)", Pattern.DOTALL);
        Matcher matcher = field.matcher(json);
        if (!matcher.find()) throw new IllegalArgumentException("JSON field missing: " + name);
        return matcher.group(1) == null ? null : jsonUnescape(matcher.group(1));
    }

    private static long jsonLong(String json, String name) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*(-?\\d+)").matcher(json);
        if (!matcher.find()) throw new IllegalArgumentException("JSON number field missing: " + name);
        return Long.parseLong(matcher.group(1));
    }

    private static boolean jsonBoolean(String json, String name) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*(true|false)").matcher(json);
        if (!matcher.find()) throw new IllegalArgumentException("JSON boolean field missing: " + name);
        return Boolean.parseBoolean(matcher.group(1));
    }

    private static String jsonNullableQuote(String value) { return value == null ? "null" : jsonQuote(value); }

    private static String jsonQuote(String value) {
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    private static String jsonUnescape(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '\\') { result.append(current); continue; }
            if (++index >= value.length()) throw new IllegalArgumentException("invalid JSON escape");
            char escaped = value.charAt(index);
            switch (escaped) {
                case '\\' -> result.append('\\');
                case '"' -> result.append('"');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'u' -> {
                    if (index + 4 >= value.length()) throw new IllegalArgumentException("invalid JSON unicode escape");
                    result.append((char) Integer.parseInt(value.substring(index + 1, index + 5), 16));
                    index += 4;
                }
                default -> throw new IllegalArgumentException("invalid JSON escape: " + escaped);
            }
        }
        return result.toString();
    }

    /** Narrow typed projection keeps assertions explicit without depending on map iteration order. */
    private record ResourceBudgetSnapshotWhileRestart(long remainingModelCalls, long reservedSearchExpansions) {
        static ResourceBudgetSnapshotWhileRestart from(dev.supplychain.agent.mechanisms.b6.ResourceBudgetSnapshot snapshot) {
            return new ResourceBudgetSnapshotWhileRestart(snapshot.remaining(ResourceUnit.MODEL_CALLS),
                    snapshot.reserved().get(ResourceUnit.SEARCH_EXPANSIONS));
        }
    }
}
