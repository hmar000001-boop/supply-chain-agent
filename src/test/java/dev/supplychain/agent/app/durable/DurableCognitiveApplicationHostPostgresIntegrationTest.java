package dev.supplychain.agent.app.durable;

import dev.supplychain.agent.actions.api.ActionCategory;
import dev.supplychain.agent.actions.internal.ReasoningAction;
import dev.supplychain.agent.actions.internal.ReasoningAssessment;
import dev.supplychain.agent.actions.internal.ReasoningConfidence;
import dev.supplychain.agent.actions.internal.ReasoningProvider;
import dev.supplychain.agent.core.interpretation.InterpretationOutcome;
import dev.supplychain.agent.core.interpretation.InterpretationRequest;
import dev.supplychain.agent.core.interpretation.RequestInterpretation;
import dev.supplychain.agent.infrastructure.postgres.runtime.JsonCodec;
import dev.supplychain.agent.infrastructure.postgres.runtime.ObservationProvenanceResolver;
import dev.supplychain.agent.infrastructure.postgres.runtime.PostgresRuntimeCodecs;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.working.MemoryValue;
import dev.supplychain.agent.memory.working.WorkingMemoryId;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedureDraft;
import dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeEvidence;
import dev.supplychain.agent.mechanisms.b3.WorkspaceReceiptEvidence;
import dev.supplychain.agent.mechanisms.b6.ResourceBudget;
import dev.supplychain.agent.mechanisms.b6.ResourceUnit;
import dev.supplychain.agent.mechanisms.b6.MetaControlPolicy;
import dev.supplychain.agent.mechanisms.b2.RecoveryDraftEvidenceVerifier;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedureApprovalAuthority;
import dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeEvidenceVerifier;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntention;
import dev.supplychain.agent.mechanisms.b2.ImpasseResolutionCase;
import dev.supplychain.agent.mechanisms.b2.ResolutionFinding;
import dev.supplychain.agent.actions.api.ActionFailure;
import dev.supplychain.agent.actions.api.ActionReceipt;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleState;
import dev.supplychain.agent.runtime.cognitive.ResourceBudgetExecutionGuard;
import dev.supplychain.agent.runtime.cognitive.decision.PlanningArtifactStore;
import dev.supplychain.agent.runtime.cognitive.lifecycle.AuthorizedActionPlan;
import dev.supplychain.agent.runtime.cognitive.lifecycle.ImpasseChildArtifactStore;
import dev.supplychain.agent.runtime.cognitive.lifecycle.RecoveryProcedureDraftRequestFactory;
import dev.supplychain.agent.runtime.langchain4j.BudgetedReasoningProvider;
import dev.supplychain.agent.planning.api.*;
import dev.supplychain.agent.planning.api.PlanningResult;
import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.api.PlanningStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual durable app composition against the CI PostgreSQL service. */
class DurableCognitiveApplicationHostPostgresIntegrationTest {

    @Test
    @EnabledIfSystemProperty(named = "phase0.postgres", matches = "true")
    void durableHostPersistsMemoryRestoresInterruptedCycleAndAcceptsANewTopLevelCycle() throws Exception {
        MemoryScopeId scope = new MemoryScopeId("durable-host-it-" + UUID.randomUUID());
        Map<String, String> environment = Map.copyOf(System.getenv());
        var ports = ports(scope);
        WorkingMemoryId durableMemoryId = new WorkingMemoryId("restart-memory-" + UUID.randomUUID());
        Instant memoryNow = Instant.now();
        String firstThread;

        try (var firstHost = DurableCognitiveApplicationHost.open(environment, ports)) {
            assertHostComposition(scope, firstHost);
            var memory = firstHost.workingMemory().open(durableMemoryId, scope, memoryNow);
            firstHost.workingMemory().put(durableMemoryId, memory.version(), "restart-marker",
                    new MemoryValue.Text("persisted-before-host-close"),
                    List.of(new EvidenceReference("integration-test", "restart-marker")), memoryNow.plusMillis(1));

            var firstCycle = firstHost.start(new DurableCognitiveApplicationHost.CycleRequest(
                    "request-cycle-1", "cycle-1", scope, "Assess the supplier capacity disruption."))
                    .orElseThrow(() -> new AssertionError("first cycle produced no checkpointed output"));
            assertTrue(firstCycle.interrupted(), "cycle must pause at the clarification interrupt boundary");
            assertEquals("NEEDS_CLARIFICATION", firstCycle.state().text(CognitiveCycleState.DECISION_STATUS));
            firstThread = firstHost.threadId(scope.value(), "cycle-1");
        }

        // A second host instance uses the same PostgreSQL database and reconstructs the graph/saver.
        try (var restartedHost = DurableCognitiveApplicationHost.open(environment, ports)) {
            assertHostComposition(scope, restartedHost);
            var restoredMemory = restartedHost.workingMemory().load(durableMemoryId);
            assertEquals(new MemoryValue.Text("persisted-before-host-close"),
                    restoredMemory.values().get("restart-marker").value());

            // Resume the exact interrupted execution after host restart. The deterministic test
            // interpreter asks one more clarification, so the graph checkpoints again without
            // entering planning/effects; this verifies restart + resume without fake authorization.
            var resumed = restartedHost.resume(scope.value(), "cycle-1",
                    new DurableCognitiveApplicationHost.ClarificationReply("The affected order is SO-100."))
                    .orElseThrow(() -> new AssertionError("resumed cycle produced no checkpointed output"));
            assertTrue(resumed.interrupted(), "resumed cycle must preserve the next explicit clarification boundary");
            assertEquals("NEEDS_CLARIFICATION", resumed.state().text(CognitiveCycleState.DECISION_STATUS));
            assertEquals(firstThread, restartedHost.threadId(scope.value(), "cycle-1"));

            // The same host accepts a new independent top-level cycle with a distinct durable thread.
            var secondCycle = restartedHost.start(new DurableCognitiveApplicationHost.CycleRequest(
                    "request-cycle-2", "cycle-2", scope, "Assess a second supplier disruption."))
                    .orElseThrow(() -> new AssertionError("second cycle produced no checkpointed output"));
            assertTrue(secondCycle.interrupted(), "second top-level cycle must checkpoint at clarification");
            assertEquals("NEEDS_CLARIFICATION", secondCycle.state().text(CognitiveCycleState.DECISION_STATUS));
            assertNotEquals(firstThread, restartedHost.threadId(scope.value(), "cycle-2"));
            assertThrows(IllegalArgumentException.class, () -> new DurableCognitiveApplicationHost.HumanControlReply(" ", "actor"));
            assertThrows(IllegalArgumentException.class, () -> new DurableCognitiveApplicationHost.ApprovalReply("approval-response", " "));
            assertThrows(NullPointerException.class, () -> restartedHost.resume(scope.value(), "cycle-2", null));
            assertThrows(SecurityException.class, () -> restartedHost.threadId("another-tenant", "cycle-1"));
            assertThrows(SecurityException.class, () -> restartedHost.start(new DurableCognitiveApplicationHost.CycleRequest(
                    "request-cross-scope", "execution-cross-scope", new MemoryScopeId("another-tenant"), "test request")));
        }
    }

    private static void assertHostComposition(MemoryScopeId scope, DurableCognitiveApplicationHost host) {
        assertEquals(scope, host.memoryScopeId());
        assertEquals(scope.value(), host.runtimePersistence().scopeId());
        assertNotNull(host.graph());
        assertNotNull(host.resourceBudgetGuard());
        assertEquals(23, host.actionRegistry().descriptors().size());
        assertEquals(10, host.actionRegistry().descriptors().stream()
                .filter(descriptor -> descriptor.category() == ActionCategory.LEARNING).count());
        assertTrue(host.actionRegistry().descriptors().stream()
                .anyMatch(descriptor -> descriptor.id().value().equals("memory.procedural.activate.v1")));
        var reasoning = (ReasoningAction) host.actionRegistry().findHandler(ReasoningAction.ID).orElseThrow();
        assertTrue(reasoning.isBoundToBudgetIdentity(host.resourceBudgetGuard()),
                "the registered reasoning action must use the exact shared durable B6 guard");
    }

    private static DurableCognitiveApplicationHost.Ports ports(MemoryScopeId scope) {
        Clock clock = Clock.systemUTC();
        List<PlanningStrategy> planners = new ArrayList<>();
        for (PlannerId id : PlannerId.values()) {
            planners.add(new PlanningStrategy() {
                @Override public PlannerId id() { return id; }
                @Override public PlanningResult plan(PlanningRequest request) {
                    throw new UnsupportedOperationException("planner must not run in a composition-only integration test");
                }
            });
        }
        EnumMap<ResourceUnit, Long> limits = new EnumMap<>(ResourceUnit.class);
        for (ResourceUnit unit : ResourceUnit.values()) limits.put(unit, 10_000L);
        ResourceBudget budget = new ResourceBudget(limits, Instant.now().plus(1, ChronoUnit.DAYS));
        JsonCodec<PlanningArtifactStore.PlanningArtifacts> planning = unusedCodec();
        JsonCodec<AuthorizedActionPlan> approvals = unusedCodec();
        JsonCodec<ImpasseChildArtifactStore.ImpasseChildArtifact> children = unusedCodec();
        JsonCodec<CognitiveCycleOperations.ExternalObservation> observations = unusedCodec();
        JsonCodec<RecoveryIntention> intentions = unusedCodec();
        JsonCodec<ImpasseResolutionCase> impasses = unusedCodec();
        JsonCodec<List<ResolutionFinding>> findings = unusedCodec();
        JsonCodec<ActionReceipt> receipts = unusedCodec();
        JsonCodec<ActionFailure> failures = unusedCodec();
        PostgresRuntimeCodecs runtimeCodecs = PostgresRuntimeCodecs.withDefaults(
                planning, approvals, children, observations,
                ignored -> new ObservationProvenanceResolver.ObservationProvenance(
                        "unused-execution", "unused-principal", "unused-source", Instant.now()),
                intentions, impasses, findings, receipts, failures);

        RecoveryDraftEvidenceVerifier recoveryEvidence = (memoryScope, evidence, at) -> false;
        RecoveryProcedureApprovalAuthority recoveryApproval = (draft, approver, approval, at) -> false;
        RecoveryProcedureDraftRequestFactory recoveryDrafts = (context, resolved, result, parent, at) -> Optional.empty();
        DownstreamOutcomeEvidenceVerifier outcomes =
                (memoryScope, receiptId, candidateId, signal, evidence, at) -> false;
        var r2 = new DurableR2Configuration(recoveryEvidence, recoveryApproval, recoveryDrafts, outcomes,
                "durable-host-it-admission-v1", "durable-host-it-outcome-verifier", null, null);

        ReasoningProviderFactory providerFactory = guard -> new BudgetedReasoningProvider(request ->
                new ReasoningAssessment("Composition test provider", List.of(), List.of(), List.of(),
                        List.of(), List.of(), ReasoningConfidence.UNASSESSED,
                        "No reasoning invocation is expected in this test"), guard);

        return DurableCognitiveApplicationHost.Ports.productionDefaults(
                scope,
                proxy(dev.supplychain.agent.runtime.cognitive.decision.CognitiveCycleDecisionPort.class),
                lifecyclePort(),
                proxy(dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleAuthorityPort.class),
                request -> new RequestInterpretation(InterpretationOutcome.NEEDS_CLARIFICATION,
                        request.originalText(), "Composition-only test"),
                proxy(PlanningWorldModel.class),
                proxy(CandidateEvaluator.class),
                planners, clock, providerFactory,
                proxy(dev.supplychain.agent.actions.external.perception.SupplyChainPerceptionPort.class),
                proxy(dev.supplychain.agent.actions.external.grounding.SupplyChainSimulationWorld.class),
                Map.of(), ignored -> budget, runtimeCodecs,
                unusedCodec(), unusedCodec(), unusedCodec(), unusedCodec(), r2);
    }


    private static dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort lifecyclePort() {
        var type = dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort.class;
        return (dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleLifecyclePort)
                Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "CI durable lifecycle test port";
                            case "hashCode" -> System.identityHashCode(instance);
                            case "equals" -> instance == args[0];
                            default -> null;
                        };
                    }
                    if (method.getName().equals("interpretationRequest")) {
                        var context = (CognitiveCycleOperations.CycleContext) args[0];
                        return new InterpretationRequest(context.request(), context.clarificationContext(), context.clarificationRound());
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> result) {
        if (result == boolean.class) return false;
        if (result == byte.class) return (byte) 0;
        if (result == short.class) return (short) 0;
        if (result == int.class) return 0;
        if (result == long.class) return 0L;
        if (result == float.class) return 0f;
        if (result == double.class) return 0d;
        if (Optional.class.isAssignableFrom(result)) return Optional.empty();
        if (List.class.isAssignableFrom(result)) return List.of();
        if (Map.class.isAssignableFrom(result)) return Map.of();
        if (java.util.Set.class.isAssignableFrom(result)) return java.util.Set.of();
        return null;
    }

    @FunctionalInterface
    private interface ReasoningProviderFactory extends java.util.function.Function<ResourceBudgetExecutionGuard, ReasoningProvider> { }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "CI composition proxy " + type.getSimpleName();
                    case "hashCode" -> System.identityHashCode(instance);
                    case "equals" -> instance == args[0];
                    default -> null;
                };
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static <T> JsonCodec<T> unusedCodec() {
        return new JsonCodec<>() {
            @Override public String encode(T value) { throw new UnsupportedOperationException("unused codec in composition-only test"); }
            @Override public T decode(String json) { throw new UnsupportedOperationException("unused codec in composition-only test"); }
        };
    }
}
