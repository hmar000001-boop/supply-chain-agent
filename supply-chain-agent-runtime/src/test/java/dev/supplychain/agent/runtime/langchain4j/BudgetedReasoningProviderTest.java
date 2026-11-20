package dev.supplychain.agent.runtime.langchain4j;

import dev.supplychain.agent.actions.api.*;
import dev.supplychain.agent.actions.internal.ReasoningAction;
import dev.supplychain.agent.actions.internal.ReasoningAssessment;
import dev.supplychain.agent.actions.internal.ReasoningConfidence;
import dev.supplychain.agent.actions.internal.ReasoningRequest;
import dev.supplychain.agent.mechanisms.b6.*;
import dev.supplychain.agent.runtime.cognitive.ResourceBudgetExecutionGuard;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.inmemory.InMemoryEpisodeRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BudgetedReasoningProviderTest {
    private static final Instant NOW = Instant.parse("2026-11-20T18:00:00Z");

    @Test
    void realM3ReasoningActionReservesAndCommitsDurableB6InvocationUsage() {
        var ledger = new InMemoryResourceBudgetLedger();
        var budget = new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 1L,
                ResourceUnit.REASONING_EVALUATIONS, 1L), NOW.plusSeconds(60));
        var providerCalls = new AtomicInteger();
        var delegate = (dev.supplychain.agent.actions.internal.ReasoningProvider) request -> {
            providerCalls.incrementAndGet();
            return assessment(request);
        };
        var guard = new ResourceBudgetExecutionGuard(ledger, execution -> budget, Clock.fixed(NOW, ZoneOffset.UTC));
        var budgeted = new BudgetedReasoningProvider(delegate, guard);
        var action = new ReasoningAction(budgeted);
        assertTrue(action.isBoundToBudgetIdentity(guard));
        assertFalse(action.isBoundToBudgetIdentity(new ResourceBudgetExecutionGuard(ledger, execution -> budget,
                Clock.fixed(NOW, ZoneOffset.UTC))));
        var dispatcher = new ActionDispatcher(new ActionRegistry().register(action),
                new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        var request = request();

        var first = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(),
                context("execution-budgeted", "corr-1"), request);
        var second = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(),
                context("execution-budgeted", "corr-2"), request);

        assertEquals(ActionStatus.SUCCEEDED, first.status());
        assertEquals(ActionStatus.FAILED, second.status());
        assertEquals(ActionFailureCode.RESOURCE_BUDGET_EXHAUSTED, second.failure().code());
        assertFalse(second.failure().retryable());
        assertEquals(1, providerCalls.get(), "exhausted budget must block the next provider invocation");
        var snapshot = ledger.snapshot("execution-budgeted", NOW);
        assertEquals(1L, snapshot.consumed().get(ResourceUnit.MODEL_CALLS));
        assertEquals(1L, snapshot.consumed().get(ResourceUnit.REASONING_EVALUATIONS));
        assertEquals(0L, snapshot.reserved().getOrDefault(ResourceUnit.MODEL_CALLS, 0L));
    }

    @Test
    void providerFailureStillChargesTheInvocationAndDoesNotLeakAnActiveReservation() {
        var ledger = new InMemoryResourceBudgetLedger();
        var budget = new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 2L,
                ResourceUnit.REASONING_EVALUATIONS, 2L), NOW.plusSeconds(60));
        var providerCalls = new AtomicInteger();
        var delegate = (dev.supplychain.agent.actions.internal.ReasoningProvider) request -> {
            providerCalls.incrementAndGet();
            throw new IllegalStateException("provider timed out after invocation");
        };
        var action = new ReasoningAction(new BudgetedReasoningProvider(delegate, new ResourceBudgetExecutionGuard(ledger,
                execution -> budget, Clock.fixed(NOW, ZoneOffset.UTC))));
        var dispatcher = new ActionDispatcher(new ActionRegistry().register(action),
                new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());

        var result = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(),
                context("execution-provider-failure", "corr-provider-failure"), request());

        assertEquals(ActionStatus.FAILED, result.status());
        assertEquals(ActionFailureCode.TRANSIENT_FAILURE, result.failure().code());
        assertTrue(result.failure().retryable());
        assertEquals(1, providerCalls.get());
        var snapshot = ledger.snapshot("execution-provider-failure", NOW);
        assertEquals(1L, snapshot.consumed().get(ResourceUnit.MODEL_CALLS));
        assertEquals(1L, snapshot.consumed().get(ResourceUnit.REASONING_EVALUATIONS));
        assertEquals(0L, snapshot.reserved().getOrDefault(ResourceUnit.MODEL_CALLS, 0L));
    }

    @Test
    void budgetLedgerOutageFailsClosedBeforeCallingTheProvider() {
        ResourceBudgetLedger unavailable = new ResourceBudgetLedger() {
            @Override public void register(String executionId, ResourceBudget budget) {
                throw new IllegalStateException("database unavailable");
            }
            @Override public BudgetReservation reserve(String executionId, String reservationId, ResourceUsage requested, Instant at) {
                throw new AssertionError("reserve must not run when account initialization is unavailable");
            }
            @Override public ResourceBudgetSnapshot commit(String executionId, String reservationId, ResourceUsage actual, Instant at) {
                throw new AssertionError("commit must not run when account initialization is unavailable");
            }
            @Override public ResourceBudgetSnapshot release(String executionId, String reservationId, Instant at) {
                throw new AssertionError("release must not run when account initialization is unavailable");
            }
            @Override public ResourceBudgetSnapshot snapshot(String executionId, Instant at) {
                throw new IllegalStateException("database unavailable");
            }
        };
        var providerCalls = new AtomicInteger();
        var delegate = (dev.supplychain.agent.actions.internal.ReasoningProvider) request -> {
            providerCalls.incrementAndGet();
            return assessment(request);
        };
        var budgeted = new BudgetedReasoningProvider(delegate, new ResourceBudgetExecutionGuard(unavailable,
                execution -> new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 2L), NOW.plusSeconds(60)),
                Clock.fixed(NOW, ZoneOffset.UTC)));
        var action = new ReasoningAction(budgeted);
        var dispatcher = new ActionDispatcher(new ActionRegistry().register(action),
                new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());

        var result = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(),
                context("execution-db-outage", "corr-db-outage"), request());

        assertEquals(ActionStatus.FAILED, result.status());
        assertEquals(ActionFailureCode.TRANSIENT_FAILURE, result.failure().code());
        assertEquals(0, providerCalls.get(), "no in-memory fallback may run the model without durable budget accounting");
    }

    @Test
    void executionCannotBeSilentlyReboundToDifferentBudgetLimits() {
        var ledger = new InMemoryResourceBudgetLedger();
        var calls = new AtomicInteger();
        var delegate = (dev.supplychain.agent.actions.internal.ReasoningProvider) request -> {
            calls.incrementAndGet();
            return assessment(request);
        };
        var action = new ReasoningAction(new BudgetedReasoningProvider(delegate, new ResourceBudgetExecutionGuard(ledger,
                execution -> new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 3L), NOW.plusSeconds(60)),
                Clock.fixed(NOW, ZoneOffset.UTC))));
        var dispatcher = new ActionDispatcher(new ActionRegistry().register(action),
                new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(),
                context("execution-budget-rebind", "corr-first"), request());

        var changedBudget = new BudgetedReasoningProvider(delegate, new ResourceBudgetExecutionGuard(ledger,
                execution -> new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 30L), NOW.plusSeconds(60)),
                Clock.fixed(NOW, ZoneOffset.UTC)));
        var changedAction = new ReasoningAction(changedBudget);
        var changedDispatcher = new ActionDispatcher(new ActionRegistry().register(changedAction),
                new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        var result = changedDispatcher.dispatch(changedAction.descriptor().id(), changedAction.inputType(), changedAction.outputType(),
                context("execution-budget-rebind", "corr-second"), request());

        assertEquals(ActionStatus.REJECTED, result.status());
        assertEquals(ActionFailureCode.AUTHORIZATION_DENIED, result.failure().code());
        assertEquals(1, calls.get());
    }

    private static ActionContext context(String executionId, String correlationId) {
        return ActionContext.create(executionId, correlationId,
                new ActionPrincipal("reasoning-agent", Set.of(CapabilityGrant.all(Capability.REASONING_INVOKE))),
                NOW, null);
    }

    private static ReasoningRequest request() {
        return new ReasoningRequest("Assess supplier status", "Supplier reported a delay",
                List.of(new EvidenceReference("supplier-event", "EVENT-42")));
    }

    private static ReasoningAssessment assessment(ReasoningRequest request) {
        return new ReasoningAssessment("Supplier delay is reported", List.of("Supplier reported a delay"),
                List.of(), List.of(), request.evidence(), List.of(), ReasoningConfidence.MEDIUM,
                "The statement is limited to supplied evidence");
    }
}
