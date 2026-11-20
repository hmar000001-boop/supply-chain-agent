package dev.supplychain.agent.runtime.cognitive.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.supplychain.agent.actions.api.ActionCategory;
import dev.supplychain.agent.actions.api.ActionContext;
import dev.supplychain.agent.actions.api.ActionDescriptor;
import dev.supplychain.agent.actions.api.ActionDispatcher;
import dev.supplychain.agent.actions.api.ActionStatus;
import dev.supplychain.agent.actions.api.ActionEffect;
import dev.supplychain.agent.actions.api.ActionLedgerClaim;
import dev.supplychain.agent.actions.api.ActionReceipt;
import dev.supplychain.agent.actions.api.ActionFailure;
import dev.supplychain.agent.actions.api.FencedActionExecutionLedger;
import dev.supplychain.agent.actions.api.IdempotencyLedgerKey;
import dev.supplychain.agent.actions.api.ActionHandler;
import dev.supplychain.agent.actions.api.ActionId;
import dev.supplychain.agent.actions.api.ActionPrincipal;
import dev.supplychain.agent.actions.api.ActionRegistry;
import dev.supplychain.agent.actions.api.Capability;
import dev.supplychain.agent.actions.api.CapabilityAuthorizer;
import dev.supplychain.agent.actions.api.CapabilityGrant;
import dev.supplychain.agent.actions.api.InMemoryActionExecutionLedger;
import dev.supplychain.agent.mechanisms.b6.InMemoryResourceBudgetLedger;
import dev.supplychain.agent.mechanisms.b6.LeasedResourceBudgetLedger;
import dev.supplychain.agent.mechanisms.b6.BudgetReservationLease;
import dev.supplychain.agent.mechanisms.b6.ResourceBudgetSnapshot;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import dev.supplychain.agent.mechanisms.b6.ResourceBudget;
import dev.supplychain.agent.mechanisms.b6.ResourceUnit;
import dev.supplychain.agent.runtime.cognitive.ResourceBudgetExecutionGuard;
import dev.supplychain.agent.runtime.cognitive.ResourceBudgetedActionExecution;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

final class ActionDispatchCommandTest {
    private static final Instant NOW = Instant.parse("2026-11-17T22:00:00Z");
    private static final ActionId ACTION_ID = new ActionId("test.grounding.inventory.reserve");

    @Test
    void typedPlanCommandUsesM3DispatcherAndIdempotencyLedger() {
        AtomicInteger executions = new AtomicInteger();
        ActionRegistry registry = new ActionRegistry().register(handler(executions));
        ActionDispatcher dispatcher = new ActionDispatcher(registry, new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        ActionDispatchCommand<String, String> command = new ActionDispatchCommand<>(
                "reserve:lot-1", ACTION_ID, String.class, String.class, "lot-1:10");
        ActionPrincipal principal = new ActionPrincipal("operator-1",
                Set.of(CapabilityGrant.all(Capability.INVENTORY_RESERVE)));
        ActionContext context = ActionContext.create("execution:m6b", "request:m6b", principal, NOW, "idempotency:m6b:1");

        var first = command.dispatch(dispatcher, context);
        var retry = command.dispatch(dispatcher, context);

        assertEquals(dev.supplychain.agent.actions.api.ActionStatus.SUCCEEDED, first.status());
        assertEquals(dev.supplychain.agent.actions.api.ActionStatus.ALREADY_COMPLETED, retry.status());
        assertEquals(1, executions.get());
        assertTrue(retry.receiptOptional().isPresent());
    }

    @Test
    void externalDispatchUsesTheSharedB6BudgetAndM3DuplicateDoesNotConsumeItTwice() {
        AtomicInteger executions = new AtomicInteger();
        var ledger = new InMemoryResourceBudgetLedger();
        var budget = new ResourceBudget(Map.of(ResourceUnit.ACTION_DISPATCHES, 1L), NOW.plusSeconds(60));
        var guard = new ResourceBudgetExecutionGuard(ledger, ignored -> budget, Clock.fixed(NOW, ZoneOffset.UTC));
        var registry = new ActionRegistry().register(handler(executions));
        var dispatcher = new ActionDispatcher(registry, new CapabilityAuthorizer(),
                new InMemoryActionExecutionLedger(), new ResourceBudgetedActionExecution(guard));
        var command = new ActionDispatchCommand<>(
                "reserve:lot-1", ACTION_ID, String.class, String.class, "lot-1:10");
        ActionPrincipal principal = new ActionPrincipal("operator-1",
                Set.of(CapabilityGrant.all(Capability.INVENTORY_RESERVE)));
        ActionContext firstContext = ActionContext.create("execution:m3-budget", "request:m3-budget", principal,
                NOW, "idempotency:m3-budget:1");

        var first = command.dispatch(dispatcher, firstContext);
        var duplicate = command.dispatch(dispatcher, firstContext);

        assertEquals(ActionStatus.SUCCEEDED, first.status());
        assertEquals(ActionStatus.ALREADY_COMPLETED, duplicate.status());
        assertEquals(1, executions.get());
        assertTrue(dispatcher.isBoundToBudgetIdentity(guard));
        assertEquals(1L, ledger.snapshot("execution:m3-budget", NOW).consumed()
                .get(ResourceUnit.ACTION_DISPATCHES));

        ActionContext secondContext = ActionContext.create("execution:m3-budget", "request:m3-budget", principal,
                NOW, "idempotency:m3-budget:2");
        var exhausted = command.dispatch(dispatcher, secondContext);

        assertEquals(ActionStatus.FAILED, exhausted.status());
        assertEquals(dev.supplychain.agent.actions.api.ActionFailureCode.RESOURCE_BUDGET_EXHAUSTED,
                exhausted.failureOptional().orElseThrow().code());
        assertEquals(1, executions.get(), "B6 exhaustion must reject before a second external handler invocation");
        assertEquals(1L, ledger.snapshot("execution:m3-budget", NOW).consumed()
                .get(ResourceUnit.ACTION_DISPATCHES));
    }


    @Test
    void successfulExternalEffectWithFailedB6CommitIsReportedAsUnknownOutcome() {
        AtomicInteger executions = new AtomicInteger();
        var delegate = new InMemoryResourceBudgetLedger();
        LeasedResourceBudgetLedger commitFailingLedger = new LeasedResourceBudgetLedger() {
            @Override public void register(String executionId, ResourceBudget budget) { delegate.register(executionId, budget); }
            @Override public void ensureRegistered(String executionId, ResourceBudget budget) { delegate.ensureRegistered(executionId, budget); }
            @Override public dev.supplychain.agent.mechanisms.b6.BudgetReservation reserve(
                    String executionId, String reservationId, ResourceUsage requested, Instant at) {
                return delegate.reserve(executionId, reservationId, requested, at);
            }
            @Override public ResourceBudgetSnapshot commit(String executionId, String reservationId,
                                                            ResourceUsage actual, Instant at) {
                return delegate.commit(executionId, reservationId, actual, at);
            }
            @Override public ResourceBudgetSnapshot release(String executionId, String reservationId, Instant at) {
                return delegate.release(executionId, reservationId, at);
            }
            @Override public ResourceBudgetSnapshot snapshot(String executionId, Instant at) {
                return delegate.snapshot(executionId, at);
            }
            @Override public BudgetReservationLease acquireLease(String executionId, String reservationId,
                    String ownerId, ResourceUsage requested, Instant leaseExpiresAt, Instant at) {
                return delegate.acquireLease(executionId, reservationId, ownerId, requested, leaseExpiresAt, at);
            }
            @Override public BudgetReservationLease markOperationStarted(String executionId, String reservationId,
                    String ownerId, long fencingToken, Instant at) {
                return delegate.markOperationStarted(executionId, reservationId, ownerId, fencingToken, at);
            }
            @Override public BudgetReservationLease renewLease(String executionId, String reservationId,
                    String ownerId, long fencingToken, Instant leaseExpiresAt, Instant at) {
                return delegate.renewLease(executionId, reservationId, ownerId, fencingToken, leaseExpiresAt, at);
            }
            @Override public ResourceBudgetSnapshot commitOwned(String executionId, String reservationId,
                    String ownerId, long fencingToken, ResourceUsage actual, Instant at) {
                throw new SecurityException("lease was fenced after external action completion");
            }
            @Override public ResourceBudgetSnapshot releaseOwnedBeforeStart(String executionId, String reservationId,
                    String ownerId, long fencingToken, Instant at) {
                return delegate.releaseOwnedBeforeStart(executionId, reservationId, ownerId, fencingToken, at);
            }
            @Override public int recoverExpiredReservations(String executionId, Instant at, int maximum) {
                return delegate.recoverExpiredReservations(executionId, at, maximum);
            }
            @Override public Optional<BudgetReservationLease> findLease(String executionId, String reservationId) {
                return delegate.findLease(executionId, reservationId);
            }
        };
        var budget = new ResourceBudget(Map.of(ResourceUnit.ACTION_DISPATCHES, 2L), NOW.plusSeconds(60));
        var guard = new ResourceBudgetExecutionGuard(commitFailingLedger, ignored -> budget,
                Clock.fixed(NOW, ZoneOffset.UTC));
        var registry = new ActionRegistry().register(handler(executions));
        var dispatcher = new ActionDispatcher(registry, new CapabilityAuthorizer(),
                new InMemoryActionExecutionLedger(), new ResourceBudgetedActionExecution(guard));
        var command = new ActionDispatchCommand<>("reserve:lot-1", ACTION_ID, String.class, String.class, "lot-1:10");
        ActionPrincipal principal = new ActionPrincipal("operator-1",
                Set.of(CapabilityGrant.all(Capability.INVENTORY_RESERVE)));
        ActionContext context = ActionContext.create("execution:m3-commit-failure", "request:m3-commit-failure",
                principal, NOW, "idempotency:m3-commit-failure");

        var result = command.dispatch(dispatcher, context);

        assertEquals(ActionStatus.FAILED, result.status());
        assertEquals(dev.supplychain.agent.actions.api.ActionFailureCode.UNKNOWN_OUTCOME,
                result.failureOptional().orElseThrow().code());
        var retry = command.dispatch(dispatcher, context);
        assertEquals(ActionStatus.FAILED, retry.status());
        assertEquals(dev.supplychain.agent.actions.api.ActionFailureCode.UNKNOWN_OUTCOME,
                retry.failureOptional().orElseThrow().code());
        assertEquals(1, executions.get(), "successful external effect must not be repeated when B6 commit fails");
    }

    @Test
    void successfulExternalEffectWithFailedM3ReceiptCommitIsReportedAsUnknownOutcome() {
        AtomicInteger executions = new AtomicInteger();
        var delegate = new InMemoryActionExecutionLedger();
        FencedActionExecutionLedger receiptFailingLedger = new FencedActionExecutionLedger() {
            @Override public java.util.Optional<ActionLedgerClaim> inspect(IdempotencyLedgerKey key, String fingerprint) {
                return delegate.inspect(key, fingerprint);
            }
            @Override public FencedClaim claimFenced(IdempotencyLedgerKey key, String fingerprint, Instant startedAt) {
                return new FencedClaim(delegate.claim(key, fingerprint, startedAt), 1L);
            }
            @Override public void completeFenced(IdempotencyLedgerKey key, long token, ActionReceipt receipt) {
                throw new SecurityException("M3 receipt commit was fenced after the action returned");
            }
            @Override public void failFenced(IdempotencyLedgerKey key, long token, ActionFailure failure) {
                delegate.fail(key, failure);
            }
            @Override public ActionLedgerClaim claim(IdempotencyLedgerKey key, String fingerprint, Instant startedAt) {
                return delegate.claim(key, fingerprint, startedAt);
            }
            @Override public void complete(IdempotencyLedgerKey key, ActionReceipt receipt) {
                delegate.complete(key, receipt);
            }
            @Override public void fail(IdempotencyLedgerKey key, ActionFailure failure) {
                delegate.fail(key, failure);
            }
        };
        var registry = new ActionRegistry().register(handler(executions));
        var dispatcher = new ActionDispatcher(registry, new CapabilityAuthorizer(), receiptFailingLedger);
        var command = new ActionDispatchCommand<>("reserve:lot-1", ACTION_ID, String.class, String.class, "lot-1:10");
        ActionPrincipal principal = new ActionPrincipal("operator-1",
                Set.of(CapabilityGrant.all(Capability.INVENTORY_RESERVE)));
        ActionContext context = ActionContext.create("execution:m3-receipt-failure", "request:m3-receipt-failure",
                principal, NOW, "idempotency:m3-receipt-failure");

        var result = command.dispatch(dispatcher, context);

        assertEquals(ActionStatus.FAILED, result.status());
        assertEquals(dev.supplychain.agent.actions.api.ActionFailureCode.UNKNOWN_OUTCOME,
                result.failureOptional().orElseThrow().code());
        assertTrue(result.failureOptional().orElseThrow().outcomeUnknown());
        var retry = command.dispatch(dispatcher, context);
        assertEquals(ActionStatus.FAILED, retry.status());
        assertEquals(dev.supplychain.agent.actions.api.ActionFailureCode.UNKNOWN_OUTCOME,
                retry.failureOptional().orElseThrow().code());
        assertEquals(1, executions.get(), "an M3 receipt write failure must never cause the external action to be retried");
    }

    @Test
    void b6ReservationIdentityPreventsReplayAfterM3LedgerReplacement() {
        AtomicInteger executions = new AtomicInteger();
        var b6Ledger = new InMemoryResourceBudgetLedger();
        var budget = new ResourceBudget(Map.of(ResourceUnit.ACTION_DISPATCHES, 2L), NOW.plusSeconds(60));
        var guard = new ResourceBudgetExecutionGuard(b6Ledger, ignored -> budget, Clock.fixed(NOW, ZoneOffset.UTC));
        var registry = new ActionRegistry().register(handler(executions));
        var command = new ActionDispatchCommand<>(
                "reserve:lot-1", ACTION_ID, String.class, String.class, "lot-1:10");
        ActionPrincipal principal = new ActionPrincipal("operator-1",
                Set.of(CapabilityGrant.all(Capability.INVENTORY_RESERVE)));
        ActionContext sameLogicalAttempt = ActionContext.create("execution:m3-restart", "request:m3-restart",
                principal, NOW, "idempotency:stable-after-restart");

        var firstProcessDispatcher = new ActionDispatcher(registry, new CapabilityAuthorizer(),
                new InMemoryActionExecutionLedger(), new ResourceBudgetedActionExecution(guard));
        var first = command.dispatch(firstProcessDispatcher, sameLogicalAttempt);
        assertEquals(ActionStatus.SUCCEEDED, first.status());

        // Simulate loss/replacement of the process-local M3 ledger while durable B6 state survives.
        var replacementProcessDispatcher = new ActionDispatcher(registry, new CapabilityAuthorizer(),
                new InMemoryActionExecutionLedger(), new ResourceBudgetedActionExecution(guard));
        var replay = command.dispatch(replacementProcessDispatcher, sameLogicalAttempt);

        assertEquals(ActionStatus.FAILED, replay.status());
        assertEquals(dev.supplychain.agent.actions.api.ActionFailureCode.UNKNOWN_OUTCOME,
                replay.failureOptional().orElseThrow().code());
        assertEquals(1, executions.get(), "the durable B6 reservation must fence a replay even with a fresh M3 ledger");
        assertEquals(1L, b6Ledger.snapshot("execution:m3-restart", NOW).consumed()
                .get(ResourceUnit.ACTION_DISPATCHES));
    }

    @Test
    void typedPlanCommandCannotExceedPrincipalCapabilities() {
        AtomicInteger executions = new AtomicInteger();
        ActionRegistry registry = new ActionRegistry().register(handler(executions));
        ActionDispatcher dispatcher = new ActionDispatcher(registry, new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        ActionDispatchCommand<String, String> command = new ActionDispatchCommand<>(
                "reserve:lot-1", ACTION_ID, String.class, String.class, "lot-1:10");
        ActionPrincipal principal = new ActionPrincipal("read-only-operator", Set.of());
        ActionContext context = ActionContext.create("execution:m6b", "request:m6b", principal, NOW, "idempotency:m6b:2");

        var result = command.dispatch(dispatcher, context);

        assertEquals(dev.supplychain.agent.actions.api.ActionStatus.REJECTED, result.status());
        assertEquals(0, executions.get());
    }

    private static ActionHandler<String, String> handler(AtomicInteger executions) {
        ActionDescriptor descriptor = new ActionDescriptor(ACTION_ID, "Test inventory reservation",
                "State-changing test action", ActionCategory.GROUNDING, ActionEffect.DOMAIN_WRITE,
                Capability.INVENTORY_RESERVE, true);
        return new ActionHandler<>() {
            @Override public ActionDescriptor descriptor() { return descriptor; }
            @Override public Class<String> inputType() { return String.class; }
            @Override public Class<String> outputType() { return String.class; }
            @Override public String execute(ActionContext context, String input) {
                executions.incrementAndGet();
                return "reserved:" + input;
            }
            @Override public String requestFingerprint(String input) { return "reserve:" + input; }
            @Override public String resultReference(String output) { return output; }
        };
    }
}
