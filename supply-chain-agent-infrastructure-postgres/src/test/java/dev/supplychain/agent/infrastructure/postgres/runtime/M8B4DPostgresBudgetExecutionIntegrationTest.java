package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.actions.api.ActionContext;
import dev.supplychain.agent.actions.api.ActionDispatcher;
import dev.supplychain.agent.actions.api.ActionPrincipal;
import dev.supplychain.agent.actions.api.ActionRegistry;
import dev.supplychain.agent.actions.api.ActionStatus;
import dev.supplychain.agent.actions.api.Capability;
import dev.supplychain.agent.actions.api.CapabilityAuthorizer;
import dev.supplychain.agent.actions.api.CapabilityGrant;
import dev.supplychain.agent.actions.api.InMemoryActionExecutionLedger;
import dev.supplychain.agent.actions.internal.ReasoningAction;
import dev.supplychain.agent.actions.internal.ReasoningAssessment;
import dev.supplychain.agent.actions.internal.ReasoningConfidence;
import dev.supplychain.agent.actions.internal.ReasoningProvider;
import dev.supplychain.agent.actions.internal.ReasoningRequest;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresDatabaseConfig;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresPoolSettings;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresPooledDataSourceFactory;
import dev.supplychain.agent.infrastructure.postgres.migration.PostgresMigrationRunner;
import dev.supplychain.agent.mechanisms.b6.ResourceBudget;
import dev.supplychain.agent.mechanisms.b6.ResourceUnit;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.runtime.cognitive.ResourceBudgetExecutionGuard;
import dev.supplychain.agent.runtime.langchain4j.BudgetedReasoningProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** PostgreSQL-backed proof that M5 planning and M3 reasoning charge the same durable B6 account. */
@EnabledIfSystemProperty(named = "phase0.postgres", matches = "true")
class M8B4DPostgresBudgetExecutionIntegrationTest {
    private static final PostgresPoolSettings POOL = new PostgresPoolSettings(4, 0, 5_000, 3_000, 30_000, 600_000, 0);

    @Test
    void m5PlanningAndM3ReasoningConsumeOneSharedPostgresBudgetAccount() {
        var database = PostgresDatabaseConfig.fromEnvironment(System.getenv());
        String scope = "m8b4d-budget-wiring-" + UUID.randomUUID();
        String execution = "execution:" + UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        ResourceBudget budget = new ResourceBudget(Map.of(
                ResourceUnit.SEARCH_EXPANSIONS, 100L,
                ResourceUnit.REASONING_EVALUATIONS, 20L,
                ResourceUnit.MODEL_CALLS, 5L,
                ResourceUnit.MCTS_ROLLOUTS, 50L), now.plusSeconds(900));

        try (var dataSource = PostgresPooledDataSourceFactory.create(database, POOL)) {
            new PostgresMigrationRunner(dataSource).migrate();
            var ledger = new PostgresResourceBudgetLedger(dataSource, scope);
            var guard = new ResourceBudgetExecutionGuard(ledger, ignored -> budget, Clock.fixed(now, ZoneOffset.UTC));

            record PlannerUsage() { }
            guard.execute(execution, "m5:planner-session:one",
                    new ResourceUsage(Map.of(ResourceUnit.SEARCH_EXPANSIONS, 40L,
                            ResourceUnit.REASONING_EVALUATIONS, 10L,
                            ResourceUnit.MODEL_CALLS, 2L,
                            ResourceUnit.MCTS_ROLLOUTS, 20L)),
                    PlannerUsage::new,
                    ignored -> new ResourceUsage(Map.of(ResourceUnit.SEARCH_EXPANSIONS, 7L,
                            ResourceUnit.REASONING_EVALUATIONS, 2L,
                            ResourceUnit.MODEL_CALLS, 1L,
                            ResourceUnit.MCTS_ROLLOUTS, 3L)));

            AtomicInteger providerCalls = new AtomicInteger();
            ReasoningProvider delegate = request -> {
                providerCalls.incrementAndGet();
                return new ReasoningAssessment("Supplier status requires human confirmation", List.of(),
                        List.of(), List.of("Supplier statement is not independently verified"), List.of(),
                        List.of("Authoritative supplier confirmation is absent"), ReasoningConfidence.UNASSESSED,
                        "The supplied evidence is insufficient for an operational conclusion");
            };
            var action = new ReasoningAction(new BudgetedReasoningProvider(delegate, guard));
            var dispatcher = new ActionDispatcher(new ActionRegistry().register(action),
                    new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
            var context = ActionContext.create(execution, "correlation:" + execution,
                    new ActionPrincipal("m8b4d-budgeted-reasoner",
                            Set.of(CapabilityGrant.all(Capability.REASONING_INVOKE))), now, "reasoning-idempotency");
            var request = new ReasoningRequest("Assess supplier status", "Supplier reported a delayed shipment",
                    List.of(new EvidenceReference("supplier-message", "MSG-" + execution)));

            var result = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(), context, request);
            assertEquals(ActionStatus.SUCCEEDED, result.status());
            assertEquals(1, providerCalls.get());

            var snapshot = ledger.snapshot(execution, now);
            assertEquals(7L, snapshot.consumed().get(ResourceUnit.SEARCH_EXPANSIONS));
            assertEquals(3L, snapshot.consumed().get(ResourceUnit.REASONING_EVALUATIONS));
            assertEquals(2L, snapshot.consumed().get(ResourceUnit.MODEL_CALLS));
            assertEquals(3L, snapshot.consumed().get(ResourceUnit.MCTS_ROLLOUTS));
            assertEquals(0L, snapshot.reserved().getOrDefault(ResourceUnit.SEARCH_EXPANSIONS, 0L));
            assertEquals(0L, snapshot.reserved().getOrDefault(ResourceUnit.REASONING_EVALUATIONS, 0L));
            assertEquals(0L, snapshot.reserved().getOrDefault(ResourceUnit.MODEL_CALLS, 0L));
            assertEquals(0L, snapshot.reserved().getOrDefault(ResourceUnit.MCTS_ROLLOUTS, 0L));
        }
    }

    @Test
    void closedPostgresBudgetStoreBlocksReasoningBeforeProviderInvocation() {
        var database = PostgresDatabaseConfig.fromEnvironment(System.getenv());
        String scope = "m8b4d-budget-outage-" + UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var dataSource = PostgresPooledDataSourceFactory.create(database, POOL);
        new PostgresMigrationRunner(dataSource).migrate();
        var ledger = new PostgresResourceBudgetLedger(dataSource, scope);
        dataSource.close();

        ResourceBudget budget = new ResourceBudget(Map.of(ResourceUnit.MODEL_CALLS, 2L,
                ResourceUnit.REASONING_EVALUATIONS, 2L), now.plusSeconds(300));
        var guard = new ResourceBudgetExecutionGuard(ledger, ignored -> budget, Clock.fixed(now, ZoneOffset.UTC));
        AtomicInteger providerCalls = new AtomicInteger();
        ReasoningProvider delegate = request -> {
            providerCalls.incrementAndGet();
            return new ReasoningAssessment("Unexpected provider invocation", List.of(), List.of(), List.of(),
                    List.of(), List.of(), ReasoningConfidence.UNASSESSED, "The budget store was unavailable");
        };
        var action = new ReasoningAction(new BudgetedReasoningProvider(delegate, guard));
        var dispatcher = new ActionDispatcher(new ActionRegistry().register(action),
                new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        var context = ActionContext.create("execution:budget-outage", "correlation:budget-outage",
                new ActionPrincipal("m8b4d-budgeted-reasoner",
                        Set.of(CapabilityGrant.all(Capability.REASONING_INVOKE))), now, "reasoning-idempotency");

        var result = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(), context,
                new ReasoningRequest("Assess provider status", "Supplier status is unavailable", List.of()));

        assertEquals(ActionStatus.FAILED, result.status());
        assertEquals(0, providerCalls.get(), "budget-store outage must fail closed before model invocation");
    }
}
