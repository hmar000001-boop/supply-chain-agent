package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.actions.api.ActionFailure;
import dev.supplychain.agent.actions.api.ActionFailureCode;
import dev.supplychain.agent.actions.api.ActionId;
import dev.supplychain.agent.actions.api.ActionLedgerClaim;
import dev.supplychain.agent.actions.api.ActionReceipt;
import dev.supplychain.agent.actions.api.FencedActionExecutionLedger;
import dev.supplychain.agent.actions.api.IdempotencyLedgerKey;
import dev.supplychain.agent.infrastructure.postgres.PostgresOperationalHealth;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresDataSourceFactory;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresPoolSettings;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresDatabaseConfig;
import dev.supplychain.agent.infrastructure.postgres.migration.PostgresMigrationRunner;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.domain.orders.CustomerOrderId;
import dev.supplychain.agent.domain.recovery.RecoveryConstraint;
import dev.supplychain.agent.domain.recovery.RecoveryObjective;
import dev.supplychain.agent.domain.recovery.RecoveryObjectiveId;
import dev.supplychain.agent.domain.recovery.RecoveryObjectiveStatus;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntention;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntentionId;
import dev.supplychain.agent.mechanisms.b4.RecoveryMethodReference;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntentionRepository;
import dev.supplychain.agent.runtime.cognitive.lifecycle.AuthorizedActionPlan;
import dev.supplychain.agent.runtime.cognitive.lifecycle.ActionExecutionBatch;
import dev.supplychain.agent.runtime.cognitive.lifecycle.ActionExecutionBatchStore;
import dev.supplychain.agent.runtime.cognitive.lifecycle.ActionDispatchCommand;
import dev.supplychain.agent.actions.api.ActionCategory;
import dev.supplychain.agent.actions.api.ActionDescriptor;
import dev.supplychain.agent.actions.api.ActionEffect;
import dev.supplychain.agent.actions.api.ActionStatus;
import dev.supplychain.agent.actions.api.Capability;
import dev.supplychain.agent.domain.catalog.Product;
import dev.supplychain.agent.domain.catalog.ProductId;
import dev.supplychain.agent.domain.common.Money;
import dev.supplychain.agent.domain.common.Quantity;
import dev.supplychain.agent.domain.common.UnitOfMeasure;
import dev.supplychain.agent.domain.inventory.InventoryLocation;
import dev.supplychain.agent.domain.inventory.InventoryLocationId;
import dev.supplychain.agent.domain.inventory.InventoryLot;
import dev.supplychain.agent.domain.inventory.InventoryLotId;
import dev.supplychain.agent.domain.inventory.LotQualityStatus;
import dev.supplychain.agent.domain.inventory.ReservationId;
import dev.supplychain.agent.domain.orders.CustomerOrder;
import dev.supplychain.agent.domain.orders.OrderLine;
import dev.supplychain.agent.domain.orders.OrderLineId;
import dev.supplychain.agent.domain.orders.OrderPriority;
import dev.supplychain.agent.domain.orders.OrderStatus;
import dev.supplychain.agent.domain.recovery.RecoveryAction;
import dev.supplychain.agent.domain.world.SupplyChainSnapshot;
import dev.supplychain.agent.planning.api.ActionImpactEstimate;
import dev.supplychain.agent.planning.api.PlanningAction;
import dev.supplychain.agent.planning.api.PlanCandidate;
import dev.supplychain.agent.planning.api.PlanEvaluation;
import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.api.PlannerTerminationReason;
import dev.supplychain.agent.planning.api.PlanningResult;
import dev.supplychain.agent.planning.api.PlanningStatistics;
import dev.supplychain.agent.planning.api.PlanningAuthorityPolicy;
import dev.supplychain.agent.planning.api.PlanningComplexity;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningRiskPolicy;
import dev.supplychain.agent.planning.api.PlanningScoringProfile;
import dev.supplychain.agent.planning.api.PlannerBudget;
import dev.supplychain.agent.simulator.command.SimulationCommand;
import dev.supplychain.agent.runtime.cognitive.decision.PlanningArtifactStore;
import dev.supplychain.agent.mechanisms.b2.ResolutionFinding;
import dev.supplychain.agent.mechanisms.b2.ChildResolutionResult;
import dev.supplychain.agent.mechanisms.b2.ImpasseResolutionCase;
import dev.supplychain.agent.mechanisms.b2.ImpasseSignal;
import dev.supplychain.agent.mechanisms.b2.ImpasseSeverity;
import dev.supplychain.agent.mechanisms.b2.ImpasseType;
import dev.supplychain.agent.mechanisms.b2.ParentContextSnapshot;
import dev.supplychain.agent.mechanisms.b2.ResolutionDisposition;
import dev.supplychain.agent.mechanisms.b4.DelegationReturn;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleAuthorityPort;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostgreSQL-backed M7-B2 acceptance tests. These tests are intentionally enabled only by the
 * CI PostgreSQL profile; they exercise real SQL transactions, leases, scopes and restart-safe reads.
 */
@EnabledIfSystemProperty(named = "phase0.postgres", matches = "true")
class PostgresM7B2CoreIntegrationTest {
    private static final Instant NOW = Instant.now().minusSeconds(30);
    private static final Pattern TOKEN = Pattern.compile("\\\"token\\\"\\s*:\\s*\\\"([0-9a-f]+)\\\"");
    private static DataSource dataSource;

    @BeforeAll static void initializeSchema() {
        dataSource = PostgresDataSourceFactory.create(PostgresDatabaseConfig.fromEnvironment(System.getenv()));
        assertEquals(8, new PostgresMigrationRunner(dataSource).migrate().totalMigrations());
    }

    @Test void m3LedgerIsScopeBoundIdempotentAndRequiresReconciliationAfterLeaseExpiry() throws Exception {
        JsonCodec<ActionReceipt> receipts = registryCodec();
        JsonCodec<ActionFailure> failures = registryCodec();
        String suffix = UUID.randomUUID().toString();
        String scopeA = "m7b2-ledger-a-" + suffix;
        String scopeB = "m7b2-ledger-b-" + suffix;
        IdempotencyLedgerKey key = new IdempotencyLedgerKey("operator-" + suffix, new ActionId("test.action"), "request-" + suffix);
        String fingerprint = "a".repeat(64);
        var ledgerA = new PostgresActionExecutionLedger(dataSource, scopeA, receipts, failures, Duration.ofSeconds(5));
        var ledgerB = new PostgresActionExecutionLedger(dataSource, scopeB, receipts, failures, Duration.ofSeconds(5));

        FencedActionExecutionLedger.FencedClaim first = ledgerA.claimFenced(key, fingerprint, NOW);
        FencedActionExecutionLedger.FencedClaim otherScope = ledgerB.claimFenced(key, fingerprint, NOW);
        assertEquals(ActionLedgerClaim.Status.ACQUIRED, first.claim().status());
        assertEquals(ActionLedgerClaim.Status.ACQUIRED, otherScope.claim().status(),
                "the same external key can be independent in different tenant scopes");

        ActionReceipt receipt = receipt(key.actionId(), first.claim().operationId(), key.principalId(), NOW);
        assertThrows(SecurityException.class, () -> ledgerA.completeFenced(key, first.fencingToken(),
                receipt(new ActionId("different.action"), first.claim().operationId(), key.principalId(), NOW)),
                "a receipt cannot be persisted under another action identity");
        ledgerA.completeFenced(key, first.fencingToken(), receipt);
        ActionLedgerClaim replay = ledgerA.inspect(key, fingerprint).orElseThrow();
        assertEquals(ActionLedgerClaim.Status.COMPLETED, replay.status());
        assertEquals(receipt, replay.receipt());
        assertEquals(ActionLedgerClaim.Status.KEY_REUSED,
                ledgerA.claimFenced(key, "b".repeat(64), NOW.plusSeconds(1)).claim().status());

        IdempotencyLedgerKey expiringKey = new IdempotencyLedgerKey(key.principalId(), key.actionId(), "lease-" + suffix);
        var shortLease = new PostgresActionExecutionLedger(dataSource, scopeA, receipts, failures, Duration.ofMillis(30));
        FencedActionExecutionLedger.FencedClaim expiring = shortLease.claimFenced(expiringKey, fingerprint, NOW);
        Thread.sleep(120);
        ActionLedgerClaim expired = shortLease.inspect(expiringKey, fingerprint).orElseThrow();
        assertEquals(ActionLedgerClaim.Status.UNKNOWN_OUTCOME, expired.status());
        assertTrue(expired.failure().outcomeUnknown());
        shortLease.reconcileUnknownAsNotApplied(expiringKey,
                new ActionFailure(ActionFailureCode.TRANSIENT_FAILURE, "external adapter confirms no effect", true, false));
        FencedActionExecutionLedger.FencedClaim retried = shortLease.claimFenced(expiringKey, fingerprint, NOW.plusSeconds(1));
        assertEquals(ActionLedgerClaim.Status.ACQUIRED, retried.claim().status());
        assertEquals(expiring.fencingToken() + 1, retried.fencingToken());
        ActionReceipt staleReceipt = receipt(expiringKey.actionId(), expiring.claim().operationId(), key.principalId(), NOW);
        assertThrows(SecurityException.class, () -> shortLease.completeFenced(expiringKey, expiring.fencingToken(), staleReceipt),
                "a stale worker cannot overwrite the new lease holder");

        IdempotencyLedgerKey externallyCompletedKey = new IdempotencyLedgerKey(key.principalId(), key.actionId(),
                "external-completed-" + suffix);
        var tinyLease = new PostgresActionExecutionLedger(dataSource, scopeA, receipts, failures, Duration.ofMillis(30));
        var uncertain = tinyLease.claimFenced(externallyCompletedKey, fingerprint, NOW);
        Thread.sleep(120);
        assertEquals(ActionLedgerClaim.Status.UNKNOWN_OUTCOME,
                tinyLease.inspect(externallyCompletedKey, fingerprint).orElseThrow().status());
        ActionReceipt reconciledReceipt = receipt(externallyCompletedKey.actionId(), uncertain.claim().operationId(),
                externallyCompletedKey.principalId(), NOW);
        tinyLease.reconcileUnknownAsCompleted(externallyCompletedKey, reconciledReceipt);
        assertEquals(ActionLedgerClaim.Status.COMPLETED,
                tinyLease.inspect(externallyCompletedKey, fingerprint).orElseThrow().status());
    }

    @Test void m3LedgerFailsClosedOnPreScopeIdempotencyRows() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String principal = "legacy-principal-" + suffix;
        String idem = "legacy-key-" + suffix;
        String action = "test.legacy-action";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO action_operation_ledger(scope_id,principal_id,action_id,idempotency_key,request_fingerprint,
                    operation_id,state,retryable,outcome_unknown,fencing_token,started_at,updated_at,failure_payload)
                VALUES('__legacy_unscoped__',?,?,?, ?,?,'FAILED',true,false,1,clock_timestamp(),clock_timestamp(),CAST('{"legacy":true}' AS jsonb))
                """)) {
            statement.setString(1, principal);
            statement.setString(2, action);
            statement.setString(3, idem);
            statement.setString(4, "c".repeat(64));
            statement.setString(5, "legacy-operation-" + suffix);
            assertEquals(1, statement.executeUpdate());
        }
        var ledger = new PostgresActionExecutionLedger(dataSource, "new-scope-" + suffix,
                registryCodec(), registryCodec(), Duration.ofSeconds(5));
        var key = new IdempotencyLedgerKey(principal, new ActionId(action), idem);
        ActionLedgerClaim claim = ledger.claimFenced(key, "c".repeat(64), NOW).claim();
        assertEquals(ActionLedgerClaim.Status.UNKNOWN_OUTCOME, claim.status());
        assertTrue(claim.failure().outcomeUnknown());
        assertTrue(ledger.inspect(key, "c".repeat(64)).orElseThrow().failure().outcomeUnknown());
    }

    @Test void concurrentM3ClaimsAcquireExactlyOneFencingLease() throws Exception {
        String suffix = UUID.randomUUID().toString();
        var key = new IdempotencyLedgerKey("concurrent-" + suffix, new ActionId("test.concurrent"), "key-" + suffix);
        String scope = "concurrent-scope-" + suffix;
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(8);
        try {
            var tasks = java.util.stream.IntStream.range(0, 8).mapToObj(index -> executor.submit(() -> {
                var ledger = new PostgresActionExecutionLedger(dataSource, scope,
                        registryCodec(), registryCodec(), Duration.ofSeconds(15));
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start latch timed out");
                return ledger.claimFenced(key, "d".repeat(64), NOW).claim().status();
            })).toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<ActionLedgerClaim.Status> statuses = new java.util.ArrayList<>();
            for (var task : tasks) statuses.add(task.get(20, TimeUnit.SECONDS));
            assertEquals(1, statuses.stream().filter(value -> value == ActionLedgerClaim.Status.ACQUIRED).count());
            assertEquals(7, statuses.stream().filter(value -> value == ActionLedgerClaim.Status.IN_PROGRESS).count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void inboxOutboxAreTenantScopedDeduplicatedRetriedAndTransactional() throws Exception {
        String suffix = UUID.randomUUID().toString();
        var scopeA = new PostgresMessagingStore(dataSource, "message-a-" + suffix);
        var scopeB = new PostgresMessagingStore(dataSource, "message-b-" + suffix);
        String messageId = "same-message-" + suffix;
        scopeA.enqueueOutbox(messageId, "aggregate-a", "test.event", "{\"scope\":\"a\"}", NOW);
        scopeB.enqueueOutbox(messageId, "aggregate-b", "test.event", "{\"scope\":\"b\"}", NOW);
        var outA = scopeA.claimOutbox("publisher-a", NOW.plusSeconds(1), Duration.ofSeconds(30), 10);
        var outB = scopeB.claimOutbox("publisher-b", NOW.plusSeconds(1), Duration.ofSeconds(30), 10);
        assertEquals(1, outA.size());
        assertEquals(1, outB.size());
        assertEquals("aggregate-a", outA.getFirst().aggregateId());
        assertEquals("aggregate-b", outB.getFirst().aggregateId());
        scopeA.acknowledgeOutbox(messageId, outA.getFirst().leaseOwner(), NOW.plusSeconds(2));
        scopeB.acknowledgeOutbox(messageId, outB.getFirst().leaseOwner(), NOW.plusSeconds(2));

        String inboxId = "same-inbox-" + suffix;
        var first = scopeA.beginInbox("inventory-consumer", inboxId, "{\"version\":1}", "worker-a", NOW,
                Duration.ofSeconds(30));
        assertEquals(PostgresMessagingStore.InboxStatus.ACQUIRED, first.status());
        var duplicate = scopeA.beginInbox("inventory-consumer", inboxId, " { \"version\" : 1 } ", "worker-b", NOW,
                Duration.ofSeconds(30));
        assertEquals(PostgresMessagingStore.InboxStatus.IN_PROGRESS, duplicate.status());
        assertEquals(PostgresMessagingStore.InboxStatus.ACQUIRED,
                scopeB.beginInbox("inventory-consumer", inboxId, "{\"version\":2}", "worker-b", NOW,
                        Duration.ofSeconds(30)).status(), "inbox identity is isolated by tenant scope");
        scopeA.acknowledgeInbox("inventory-consumer", inboxId, first.workerOrReason(), NOW.plusSeconds(1));
        assertEquals(PostgresMessagingStore.InboxStatus.ALREADY_PROCESSED,
                scopeA.beginInbox("inventory-consumer", inboxId, "{\"version\":1}", "worker-c", NOW.plusSeconds(2),
                        Duration.ofSeconds(30)).status());
        assertEquals(PostgresMessagingStore.InboxStatus.PAYLOAD_CONFLICT,
                scopeA.beginInbox("inventory-consumer", inboxId, "{\"version\":99}", "worker-c", NOW.plusSeconds(2),
                        Duration.ofSeconds(30)).status());

        String retryOutboxId = "retry-outbox-" + suffix;
        scopeA.enqueueOutbox(retryOutboxId, "aggregate-retry", "test.retry", "{}", NOW);
        var firstRetryClaim = scopeA.claimOutbox("retry-publisher", NOW, Duration.ofSeconds(30), 10).stream()
                .filter(value -> value.messageId().equals(retryOutboxId)).findFirst().orElseThrow();
        scopeA.failOutbox(retryOutboxId, firstRetryClaim.leaseOwner(), NOW, "temporary publish outage", 3,
                Duration.ofMillis(5), Duration.ofMillis(25));
        Thread.sleep(40);
        var secondRetryClaim = scopeA.claimOutbox("retry-publisher", NOW.plusSeconds(1), Duration.ofSeconds(30), 10).stream()
                .filter(value -> value.messageId().equals(retryOutboxId)).findFirst().orElseThrow();
        assertEquals(2, secondRetryClaim.attempt());
        scopeA.acknowledgeOutbox(retryOutboxId, secondRetryClaim.leaseOwner(), NOW.plusSeconds(2));

        String retryInboxId = "retry-inbox-" + suffix;
        var firstInboxRetry = scopeA.beginInbox("retry-consumer", retryInboxId, "{\"attempt\":1}", "retry-worker", NOW,
                Duration.ofSeconds(30));
        scopeA.failInbox("retry-consumer", retryInboxId, firstInboxRetry.workerOrReason(), NOW,
                "temporary handler outage", 3, Duration.ofMillis(5), Duration.ofMillis(25));
        Thread.sleep(40);
        var secondInboxRetry = scopeA.beginInbox("retry-consumer", retryInboxId, "{\"attempt\":1}", "retry-worker", NOW.plusSeconds(1),
                Duration.ofSeconds(30));
        assertEquals(PostgresMessagingStore.InboxStatus.ACQUIRED, secondInboxRetry.status());
        scopeA.acknowledgeInbox("retry-consumer", retryInboxId, secondInboxRetry.workerOrReason(), NOW.plusSeconds(2));

        String deadOutboxId = "dead-outbox-" + suffix;
        scopeA.enqueueOutbox(deadOutboxId, "aggregate-dead", "test.dead", "{}", NOW);
        var deadOutboxClaim = scopeA.claimOutbox("dead-publisher", NOW, Duration.ofSeconds(30), 10).stream()
                .filter(value -> value.messageId().equals(deadOutboxId)).findFirst().orElseThrow();
        scopeA.failOutbox(deadOutboxId, deadOutboxClaim.leaseOwner(), NOW, "permanent failure", 1,
                Duration.ofMillis(1), Duration.ofMillis(5));
        assertTrue(scopeA.claimOutbox("dead-publisher", NOW.plusSeconds(1), Duration.ofSeconds(30), 10).stream()
                .noneMatch(value -> value.messageId().equals(deadOutboxId)), "outbox poison messages must be dead-lettered");

        String deadInboxId = "dead-inbox-" + suffix;
        var deadInboxClaim = scopeA.beginInbox("dead-consumer", deadInboxId, "{\"item\":1}", "dead-worker", NOW,
                Duration.ofSeconds(30));
        assertEquals(PostgresMessagingStore.InboxStatus.ACQUIRED, deadInboxClaim.status());
        scopeA.failInbox("dead-consumer", deadInboxId, deadInboxClaim.workerOrReason(), NOW,
                "permanent consumer failure", 1, Duration.ofMillis(1), Duration.ofMillis(5));
        assertEquals(PostgresMessagingStore.InboxStatus.DEAD_LETTER,
                scopeA.beginInbox("dead-consumer", deadInboxId, "{\"item\":1}", "new-worker", NOW.plusSeconds(1),
                        Duration.ofSeconds(30)).status());

        String rolledBackId = "rollback-" + suffix;
        assertThrows(RuntimeException.class, () -> scopeA.inTransaction(connection -> {
            scopeA.enqueueOutbox(connection, rolledBackId, "aggregate", "test.rollback", "{}", NOW);
            throw new SQLException("injected rollback after outbox enqueue");
        }));
        assertTrue(scopeA.claimOutbox("publisher-a", NOW.plusSeconds(3), Duration.ofSeconds(30), 10).isEmpty(),
                "outbox enqueue must roll back with its caller transaction");
    }

    @Test void childStepReceiptsResumeCompletedWorkAndFenceExpiredClaims() throws Exception {
        String scope = "child-step-" + UUID.randomUUID();
        JsonCodec<List<ResolutionFinding>> codec = registryCodec();
        var store = new PostgresB2ChildStepReceiptStore(dataSource, scope, codec, Duration.ofMillis(40));
        String caseId = "case-" + UUID.randomUUID();
        List<ResolutionFinding> findings = List.of(new ResolutionFinding("Supplier confirmed available stock",
                List.of(new EvidenceReference("supplier-response", "response-" + UUID.randomUUID()))));
        var first = store.claim(caseId, "step-completed", "input-1", NOW);
        assertEquals(dev.supplychain.agent.runtime.cognitive.lifecycle.ChildInvestigationStepStore.Status.ACQUIRED, first.status());
        store.complete(caseId, "step-completed", first.fencingToken(), findings, NOW.plusSeconds(1));
        var replay = store.claim(caseId, "step-completed", "input-1", NOW.plusSeconds(2));
        assertEquals(dev.supplychain.agent.runtime.cognitive.lifecycle.ChildInvestigationStepStore.Status.COMPLETED, replay.status());
        assertEquals(findings, replay.findings());
        assertThrows(SecurityException.class, () -> store.claim(caseId, "step-completed", "different-input", NOW.plusSeconds(2)));

        var expiring = store.claim(caseId, "step-expiring", "input-expiring", NOW.plusSeconds(2));
        Thread.sleep(120);
        var unknown = store.claim(caseId, "step-expiring", "input-expiring", NOW.plusSeconds(3));
        assertEquals(dev.supplychain.agent.runtime.cognitive.lifecycle.ChildInvestigationStepStore.Status.UNKNOWN_OUTCOME, unknown.status());
        store.reconcileUnknownAsNotApplied(caseId, "step-expiring", "READ_ONLY_QUERY_DID_NOT_COMPLETE", NOW.plusSeconds(4));
        var retried = store.claim(caseId, "step-expiring", "input-expiring", NOW.plusSeconds(5));
        assertEquals(dev.supplychain.agent.runtime.cognitive.lifecycle.ChildInvestigationStepStore.Status.ACQUIRED, retried.status());
        assertEquals(expiring.fencingToken() + 1, retried.fencingToken());
    }

    @Test void childArtifactsAndImpasseCasesSurviveRepositoryReplacementAndCas() {
        String scope = "b2-child-artifact-" + UUID.randomUUID();
        String caseId = "case-" + UUID.randomUUID();
        String requestId = "parent-request-" + UUID.randomUUID();
        String objectiveFingerprint = "objective-fingerprint-" + UUID.randomUUID();
        var memoryScope = new MemoryScopeId(scope);
        var parent = new ParentContextSnapshot(memoryScope, requestId, 7, objectiveFingerprint);
        var signal = new ImpasseSignal(ImpasseType.MISSING_CONFIRMATION, ImpasseSeverity.HIGH,
                "supplier:SUP-M7B2", "Supplier has not confirmed the recovery quantity",
                List.of(new EvidenceReference("supplier-response", "response:" + UUID.randomUUID())), NOW);
        var opened = ImpasseResolutionCase.open(caseId, memoryScope, "execution-" + UUID.randomUUID(), parent,
                signal, java.util.Set.of(Capability.SUPPLIER_READ), java.util.Set.of(Capability.SUPPLIER_READ),
                NOW, NOW.plus(Duration.ofHours(1)), 4);
        JsonCodec<ImpasseResolutionCase> caseCodec = registryCodec();
        var cases = new PostgresImpasseCaseRepository(dataSource, scope, caseCodec);
        cases.saveNew(opened);
        var started = opened.start(NOW.plusSeconds(1));
        assertTrue(cases.compareAndSet(caseId, opened.version(), started));
        assertFalse(cases.compareAndSet(caseId, opened.version(), started), "stale B2 lifecycle update must lose CAS");
        var findings = List.of(new ResolutionFinding("Supplier confirmed the replacement quantity",
                List.of(new EvidenceReference("supplier-response", "confirmed:" + UUID.randomUUID()))));
        var result = new ChildResolutionResult(caseId, requestId, 7, objectiveFingerprint,
                ResolutionDisposition.EVIDENCE_CONFIRMED, findings, List.of("Confirmation applies to the requested delivery window"),
                NOW.plusSeconds(2));
        var resolved = started.resolve(result, parent, NOW.plusSeconds(3));
        assertTrue(cases.compareAndSet(caseId, started.version(), resolved));
        assertEquals(resolved, new PostgresImpasseCaseRepository(dataSource, scope, caseCodec).findById(caseId).orElseThrow());

        JsonCodec<dev.supplychain.agent.runtime.cognitive.lifecycle.ImpasseChildArtifactStore.ImpasseChildArtifact> childCodec = registryCodec();
        var childArtifacts = new PostgresImpasseChildArtifactStore(dataSource, scope, childCodec);
        childArtifacts.save(caseId, result, DelegationReturn.NO_METHOD_CHANGE);
        var expected = new dev.supplychain.agent.runtime.cognitive.lifecycle.ImpasseChildArtifactStore.ImpasseChildArtifact(
                result, DelegationReturn.NO_METHOD_CHANGE);
        assertEquals(expected, new PostgresImpasseChildArtifactStore(dataSource, scope, childCodec).find(caseId).orElseThrow());
        assertTrue(new PostgresImpasseChildArtifactStore(dataSource, "other-scope", childCodec).find(caseId).isEmpty(),
                "child artifacts must not cross runtime scopes");
    }

    @Test void approvalsAreBoundToExactScopeAndDecisionAndCannotBeReadThroughAnotherTenantStore() {
        String suffix = UUID.randomUUID().toString();
        String scope = "approval-scope-" + suffix;
        String approvalId = "approval-" + suffix;
        var binding = new CognitiveCycleAuthorityPort.ApprovalBinding("request-" + suffix, "execution-" + suffix,
                scope, "candidate-set-" + suffix, "candidate-" + suffix, "intention-" + suffix, 4,
                "f".repeat(64));
        var store = new PostgresApprovalStore(dataSource, scope);
        ApprovalRecord pending = store.create(scope, approvalId, binding, "requester-" + suffix,
                "{\"capabilities\":[\"reserve\"]}", "{\"warehouse\":\"WH-1\"}",
                "evidence-" + suffix, "policy-v1", NOW.plusSeconds(600), NOW);
        assertEquals(ApprovalRecord.Status.PENDING, pending.status());
        ApprovalRecord approved = store.decide(scope, approvalId, pending.revision(), true,
                "human-reviewer-" + suffix, "response-" + suffix, "approved after review", NOW.plusSeconds(1));
        assertEquals(ApprovalRecord.Status.APPROVED, approved.status());
        assertTrue(store.isCurrentlyValid(scope, approvalId, binding, NOW.plusSeconds(2)));
        var changedBinding = new CognitiveCycleAuthorityPort.ApprovalBinding(binding.requestId(), binding.executionId(), scope,
                binding.candidateSetId(), "different-candidate", binding.intentionId(), binding.intentionVersion(),
                binding.actionPlanFingerprint());
        assertFalse(store.isCurrentlyValid(scope, approvalId, changedBinding, NOW.plusSeconds(2)));
        assertThrows(SecurityException.class, () -> new PostgresApprovalStore(dataSource, "other-scope")
                .find(scope, approvalId));
        assertThrows(IllegalStateException.class, () -> store.decide(scope, approvalId, pending.revision(), true,
                "another-reviewer", "stale-response", "stale revision", NOW.plusSeconds(3)));
        assertTrue(store.revoke(scope, approvalId, approved.revision(), "security-reviewer", "revoked by policy", NOW.plusSeconds(3)));
        assertFalse(store.isCurrentlyValid(scope, approvalId, binding, NOW.plusSeconds(4)));

        String expiringId = "approval-expiring-" + suffix;
        store.create(scope, expiringId, binding, "requester-" + suffix,
                "{\"capabilities\":[\"reserve\"]}", "{\"warehouse\":\"WH-1\"}",
                "evidence-" + suffix, "policy-v1", NOW.plusSeconds(5), NOW);
        assertFalse(store.isCurrentlyValid(scope, expiringId, binding, NOW.plusSeconds(6)));
        assertEquals(ApprovalRecord.Status.EXPIRED, store.find(scope, expiringId).orElseThrow().status());
    }

    @Test void observationsVerifyTypedContentAfterJsonbCanonicalization() {
        String scope = "observation-scope-" + UUID.randomUUID();
        JsonCodec<CognitiveCycleOperations.ExternalObservation> codec = registryCodec();
        var store = new PostgresObservationStore(dataSource, scope, codec, observation ->
                new ObservationProvenanceResolver.ObservationProvenance("execution-observation", "operator-observation",
                        "source://external/observation", NOW));
        var observation = new CognitiveCycleOperations.ExternalObservation(
                CognitiveCycleOperations.ObservationDisposition.NEEDS_EVIDENCE,
                "observation-" + UUID.randomUUID(), "evidence-set-1", "Supplier response lacks a dated commitment");
        store.save(observation);
        assertEquals(observation, store.find(observation.observationId()).orElseThrow());
        store.save(observation);
        assertThrows(SecurityException.class, () -> store.save(new CognitiveCycleOperations.ExternalObservation(
                CognitiveCycleOperations.ObservationDisposition.NOT_VERIFIED, observation.observationId(),
                "evidence-set-1", "different payload under the same observation identity")));
    }

    @Test void planningArtifactsAuthorizedPlansAndExecutionReceiptsSurviveRepositoryReplacement() {
        AuthorizedActionPlan plan = plan("m7b2-plan-" + UUID.randomUUID());
        JsonCodec<AuthorizedActionPlan> planCodec = registryCodec();
        var firstPlanStore = new PostgresAuthorizedActionPlanStore(dataSource, plan.planningRequest().workingMemory().scopeId().value(), planCodec);
        firstPlanStore.save(plan);
        var reloadedPlan = new PostgresAuthorizedActionPlanStore(dataSource,
                plan.planningRequest().workingMemory().scopeId().value(), planCodec)
                .find(plan.executionId(), plan.candidateSetId(), plan.candidateId()).orElseThrow();
        assertEquals(plan, reloadedPlan);

        var result = new PlanningResult(PlannerId.TOT_BOUNDED_SEARCH, List.of(plan.candidate()),
                PlannerTerminationReason.COMPLETED, new PlanningStatistics(3, 2, 0, 0, 0, 0, Duration.ofMillis(12)),
                "session-" + UUID.randomUUID(), "Selected the feasible candidate", List.of());
        var planningArtifacts = new PostgresPlanningArtifactStore(dataSource,
                plan.planningRequest().workingMemory().scopeId().value(), registryCodec(),
                new dev.supplychain.agent.infrastructure.postgres.planning.PostgresPlannerSessionStore(dataSource));
        String candidateSetId = planningArtifacts.store(plan.planningRequest(), result, NOW);
        assertEquals(candidateSetId, planningArtifacts.find(candidateSetId).orElseThrow().candidateSetId());

        PostgresActionExecutionBatchStore batches = new PostgresActionExecutionBatchStore(dataSource,
                plan.planningRequest().workingMemory().scopeId().value());
        String batchId = "batch-" + UUID.randomUUID();
        var started = batches.start(plan, batchId, "planner-principal", NOW);
        var item = new ActionExecutionBatch.ActionExecutionItem(0, plan.commands().getFirst().actionId().value(),
                ActionStatus.SUCCEEDED, "operation-" + UUID.randomUUID(), "result://receipt", "", "", false);
        var appended = batches.append(batchId, item, ActionExecutionBatch.BatchStatus.STARTED, NOW.plusSeconds(1));
        assertEquals(1, appended.items().size());
        assertEquals(appended, batches.append(batchId, item, ActionExecutionBatch.BatchStatus.STARTED, NOW.plusSeconds(2)),
                "an exact duplicate receipt is idempotent");
        assertEquals(ActionExecutionBatch.BatchStatus.SUCCEEDED, batches.complete(batchId, NOW.plusSeconds(3)).status());
        assertEquals(1, new PostgresActionExecutionBatchStore(dataSource,
                plan.planningRequest().workingMemory().scopeId().value()).find(batchId).orElseThrow().items().size());
        assertThrows(IllegalStateException.class, () -> batches.append(batchId,
                new ActionExecutionBatch.ActionExecutionItem(1, "another.action", ActionStatus.FAILED,
                        "", "", "FAILED", "stop", false), ActionExecutionBatch.BatchStatus.FAILED, NOW.plusSeconds(4)));
        assertThrows(IllegalStateException.class, () -> batches.append("another-batch-" + UUID.randomUUID(), item,
                ActionExecutionBatch.BatchStatus.SUCCEEDED, NOW.plusSeconds(1)),
                "unknown batch IDs follow the same state-error contract as the in-memory adapter");
    }

    @Test void readinessMetricsAndPoolDefaultsAreBoundedAndScopeFiltered() {
        var health = new PostgresOperationalHealth(dataSource);
        assertTrue(health.liveness(), "liveness is independent of transient database health");
        var readiness = health.readiness();
        assertTrue(readiness.ready(), () -> "database should be ready after V008, found " + readiness.statusCode());
        assertEquals(8, readiness.schemaVersion());

        String activeScope = "metrics-active-" + UUID.randomUUID();
        String otherScope = "metrics-other-" + UUID.randomUUID();
        new PostgresMessagingStore(dataSource, activeScope)
                .enqueueOutbox("metrics-message-" + UUID.randomUUID(), "aggregate", "metrics.event", "{}", NOW);
        assertEquals(1, new PostgresRuntimeMetrics(dataSource, activeScope).read().pendingOutbox());
        assertEquals(0, new PostgresRuntimeMetrics(dataSource, otherScope).read().pendingOutbox(),
                "operational metrics must not leak counts across tenant scopes");

        PostgresPoolSettings pool = PostgresPoolSettings.productionDefaults();
        assertEquals(10, pool.maximumPoolSize());
        assertEquals(2, pool.minimumIdle());
        assertEquals(10_000, pool.connectionTimeoutMillis());
        assertThrows(IllegalArgumentException.class,
                () -> new PostgresPoolSettings(129, 0, 10_000, 5_000, 60_000, 1_800_000, 0));
    }

    @Test void recoveryIntentionsPersistVersionHistoryAndProtectObjectiveAcrossScopeAndCas() {
        String scope = "recovery-intention-" + UUID.randomUUID();
        var objective = new RecoveryObjective(new RecoveryObjectiveId("objective-" + UUID.randomUUID()),
                "Restore affected customer orders", java.util.Set.of(new CustomerOrderId("order-" + UUID.randomUUID())),
                NOW.plusSeconds(3600), java.util.Set.of(RecoveryConstraint.NO_OVER_ALLOCATION,
                RecoveryConstraint.APPROVAL_REQUIRED_FOR_EXTERNAL_COMMITMENT), RecoveryObjectiveStatus.ACTIVE, 0);
        RecoveryIntention intention = RecoveryIntention.create(RecoveryIntentionId.random(), objective,
                new MemoryScopeId(scope), "planner", new RecoveryMethodReference("tot", "plan:tot"), NOW);
        JsonCodec<RecoveryIntention> codec = registryCodec();
        var store = new dev.supplychain.agent.infrastructure.postgres.runtime.PostgresRecoveryIntentionRepository(dataSource, scope, codec);
        store.saveNew(intention);
        var reloaded = new dev.supplychain.agent.infrastructure.postgres.runtime.PostgresRecoveryIntentionRepository(dataSource, scope, codec)
                .findById(intention.id()).orElseThrow();
        assertEquals(intention, reloaded);
        var reconsideration = reloaded.requestReconsideration("new supplier evidence", NOW.plusSeconds(1));
        assertTrue(store.compareAndSet(intention.id(), intention.version(), reconsideration));
        assertFalse(store.compareAndSet(intention.id(), intention.version(), reconsideration), "stale lifecycle writers must lose CAS");
        var changed = store.findById(intention.id()).orElseThrow();
        assertEquals(reloaded.objectiveFingerprint(), changed.objectiveFingerprint());
        assertEquals(reloaded.protectedConstraints(), changed.protectedConstraints());
        var wrongScope = new dev.supplychain.agent.infrastructure.postgres.runtime.PostgresRecoveryIntentionRepository(
                dataSource, "another-scope", codec);
        assertThrows(SecurityException.class, () -> wrongScope.saveNew(intention));
    }

    private static ActionReceipt receipt(ActionId actionId, String operationId, String principalId, Instant startedAt) {
        return new ActionReceipt(actionId, operationId, principalId, "correlation-" + operationId,
                startedAt, startedAt.plusSeconds(1), "result://" + operationId, Map.of("source", "integration-test"));
    }

    private static AuthorizedActionPlan plan(String scope) {
        var productId = new ProductId("SKU-M7B2");
        var locationId = new InventoryLocationId("WH-M7B2");
        var lotId = new InventoryLotId("LOT-M7B2");
        var orderId = new CustomerOrderId("ORDER-M7B2");
        var lineId = new OrderLineId("LINE-M7B2");
        var objectiveId = new RecoveryObjectiveId("OBJECTIVE-M7B2");
        Quantity quantity = Quantity.of("4", UnitOfMeasure.EACH);
        var objective = new RecoveryObjective(objectiveId, "Restore test order", java.util.Set.of(orderId),
                NOW.plus(Duration.ofDays(2)), java.util.Set.of(RecoveryConstraint.NO_OVER_ALLOCATION,
                RecoveryConstraint.APPROVAL_REQUIRED_FOR_EXTERNAL_COMMITMENT), RecoveryObjectiveStatus.ACTIVE, 0);
        var order = new CustomerOrder(orderId, "CUSTOMER-M7B2", OrderPriority.NORMAL, OrderStatus.OPEN,
                NOW.plus(Duration.ofDays(3)), Map.of(lineId, new OrderLine(lineId, productId, quantity,
                Quantity.zero(UnitOfMeasure.EACH))), 0);
        var world = SupplyChainSnapshot.builder(0, NOW)
                .product(new Product(productId, "SKU-M7B2", "M7-B2 integration product", UnitOfMeasure.EACH, true))
                .location(new InventoryLocation(locationId, "WH-M7B2", "Montreal", true))
                .inventoryLot(new InventoryLot(lotId, productId, locationId, quantity, LotQualityStatus.AVAILABLE,
                        NOW.plus(Duration.ofDays(30)), null, null, 0))
                .customerOrder(order).recoveryObjective(objective).build();
        var actionId = new ActionId("supply-chain.grounding.inventory.reserve");
        String actionKey = "reserve-" + UUID.randomUUID();
        var recoveryAction = new RecoveryAction.ReserveInventory(orderId, lineId, lotId, quantity);
        var simulation = new SimulationCommand.ReserveInventory(actionKey, new ReservationId("RSV-" + UUID.randomUUID()),
                orderId, lineId, lotId, quantity, NOW.plusSeconds(1));
        var action = new PlanningAction(actionKey, actionId, "Reserve available inventory", recoveryAction, simulation,
                new ActionImpactEstimate(1.0, Money.of("10", "CAD"), Duration.ofHours(1), 0.05, 0.90),
                List.of(new EvidenceReference("inventory-lot", lotId.value())), List.of());
        var evaluation = new PlanEvaluation(true, 0.9, 1.0, Money.of("10", "CAD"), Duration.ofHours(1),
                0.05, 0.9, NOW.plus(Duration.ofHours(1)), false, true, List.of());
        var candidate = new PlanCandidate("candidate:" + UUID.randomUUID(), objectiveId, List.of(action),
                List.of(new EvidenceReference("inventory-lot", lotId.value())), List.of(),
                "Reserve qualified stock", evaluation);
        var descriptor = new ActionDescriptor(actionId, "Reserve inventory", "Reserve stock",
                ActionCategory.GROUNDING, ActionEffect.DOMAIN_WRITE, Capability.INVENTORY_RESERVE, true);
        var request = new PlanningRequest("execution:" + UUID.randomUUID(), objective, world,
                dev.supplychain.agent.memory.working.WorkingMemorySnapshot.empty(new MemoryScopeId(scope), NOW,
                        NOW.plus(Duration.ofHours(1))), List.of(new EvidenceReference("evidence-set", "evidence:" + scope)),
                List.of(descriptor), objective.hardConstraints(), PlanningScoringProfile.balancedCad("1000"),
                new PlanningRiskPolicy(0.8, true),
                new PlanningAuthorityPolicy("policy:" + scope, java.util.Set.of(Capability.INVENTORY_RESERVE),
                        java.util.Set.of(Capability.INVENTORY_RESERVE)),
                new PlanningComplexity(1, 1, 0.1, 0.1),
                new PlannerBudget(2, 4, 4, 0, 0, 4, Duration.ofSeconds(30), NOW.plusSeconds(30)), NOW, null);
        var command = new ActionDispatchCommand<>(actionKey, actionId, String.class, String.class, "LOT-M7B2:4");
        return new AuthorizedActionPlan(request.executionId(), "candidate-set:" + scope, candidate.candidateId(),
                "intention:" + scope, 1, "plan-fingerprint:" + scope, candidate, request,
                List.of(command), List.of(descriptor), true);
    }

    private static <T> JsonCodec<T> registryCodec() {
        ConcurrentHashMap<String, T> registry = new ConcurrentHashMap<>();
        return new JsonCodec<>() {
            @Override public String encode(T value) {
                String token = PostgresJsonArtifactRepository.sha256(value.getClass().getName() + "|" + value);
                registry.put(token, value);
                return "{\"token\":\"" + token + "\"}";
            }
            @Override public T decode(String json) {
                Matcher matcher = TOKEN.matcher(json);
                if (!matcher.find()) throw new IllegalArgumentException("test codec JSON token missing");
                T value = registry.get(matcher.group(1));
                if (value == null) throw new IllegalStateException("test codec cannot resolve persisted token");
                return value;
            }
        };
    }
}
