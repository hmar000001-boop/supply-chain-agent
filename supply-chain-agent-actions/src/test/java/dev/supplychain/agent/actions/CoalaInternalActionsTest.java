package dev.supplychain.agent.actions;

import dev.supplychain.agent.actions.api.*;
import dev.supplychain.agent.actions.internal.*;
import dev.supplychain.agent.memory.common.*;
import dev.supplychain.agent.memory.episodic.*;
import dev.supplychain.agent.memory.inmemory.*;
import dev.supplychain.agent.memory.retrieval.*;
import dev.supplychain.agent.memory.semantic.SemanticMemoryService;
import dev.supplychain.agent.memory.working.*;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoalaInternalActionsTest {
    private static final MemoryScopeId SCOPE = new MemoryScopeId("tenant-a/execution-1");

    @Test
    void workingMemoryUpdateIsScopedVersionedAndIdempotent() {
        var repository = new InMemoryWorkingMemoryRepository();
        var service = new WorkingMemoryService(repository, Duration.ofHours(1));
        var opened = service.open(SCOPE, ActionTestFixtures.NOW);
        var handler = new WorkingMemoryUpdateAction(service);
        var dispatcher = new ActionDispatcher(new ActionRegistry().register(handler),
                new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        var evidence = new EvidenceReference("supplier-event", "EVT-11");
        var request = new WorkingMemoryUpdateRequest(SCOPE, opened.id(), 0, "disruption.summary",
                new MemoryValue.Text("Supplier capacity reduced"), List.of(evidence));
        var context = context(Capability.MEMORY_UPDATE_WORKING, "memory-write-1");

        var first = dispatcher.dispatch(handler.descriptor().id(), handler.inputType(), handler.outputType(), context, request);
        var duplicate = dispatcher.dispatch(handler.descriptor().id(), handler.inputType(), handler.outputType(), context, request);

        assertEquals(ActionStatus.SUCCEEDED, first.status());
        assertEquals(1, first.value().version());
        assertEquals(new MemoryValue.Text("Supplier capacity reduced"), first.value().values().get("disruption.summary").value());
        assertEquals(List.of(evidence), first.value().values().get("disruption.summary").evidence());
        assertEquals(ActionStatus.ALREADY_COMPLETED, duplicate.status());
        assertEquals(1, service.load(opened.id()).version());
    }

    @Test
    void workingMemoryScopeMismatchIsRejectedBeforeIdempotencyClaim() {
        var repository = new InMemoryWorkingMemoryRepository();
        var service = new WorkingMemoryService(repository, Duration.ofHours(1));
        var actualScope = new MemoryScopeId("tenant-a");
        var opened = service.open(actualScope, ActionTestFixtures.NOW);
        var action = new WorkingMemoryUpdateAction(service);
        var dispatcher = new ActionDispatcher(new ActionRegistry().register(action),
                new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        var context = ActionContext.create("exec-1", "corr-1",
                new ActionPrincipal("agent", Set.of(CapabilityGrant.all(Capability.MEMORY_UPDATE_WORKING))),
                ActionTestFixtures.NOW, "same-key");
        var forgedScope = new MemoryScopeId("tenant-b");
        var forged = new WorkingMemoryUpdateRequest(forgedScope, opened.id(), 0, "x",
                new MemoryValue.Text("not allowed"), List.of());

        var rejected = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(), context, forged);
        var valid = new WorkingMemoryUpdateRequest(actualScope, opened.id(), 0, "x",
                new MemoryValue.Text("valid"), List.of());
        var accepted = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(), context, valid);

        assertEquals(ActionStatus.REJECTED, rejected.status());
        assertEquals(ActionFailureCode.PRECONDITION_FAILED, rejected.failure().code());
        assertEquals(ActionStatus.SUCCEEDED, accepted.status(), "Rejected precondition must not claim the idempotency key");
        assertEquals(1, service.load(opened.id()).version());
    }

    @Test
    void staleWorkingMemoryVersionIsRejectedWithoutAWrite() {
        var repository = new InMemoryWorkingMemoryRepository();
        var service = new WorkingMemoryService(repository, Duration.ofHours(1));
        var opened = service.open(SCOPE, ActionTestFixtures.NOW);
        service.put(opened.id(), 0, "first", new MemoryValue.Flag(true), List.of(), ActionTestFixtures.NOW.plusSeconds(1));
        var action = new WorkingMemoryUpdateAction(service);
        var dispatcher = new ActionDispatcher(new ActionRegistry().register(action),
                new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        var context = context(Capability.MEMORY_UPDATE_WORKING, "stale-key");
        var stale = new WorkingMemoryUpdateRequest(SCOPE, opened.id(), 0, "second", new MemoryValue.Flag(true), List.of());

        var result = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(), context, stale);

        assertEquals(ActionStatus.REJECTED, result.status());
        assertEquals("STALE_MEMORY_VERSION", result.failure().message().split(":", 2)[0]);
        assertEquals(1, service.load(opened.id()).version());
    }

    @Test
    void episodeLearningRecordsButDoesNotSelfVerify() {
        var episodes = new InMemoryEpisodeRepository();
        var service = new EpisodicMemoryService(episodes, Duration.ofDays(7));
        var action = new EpisodeLearningAction(service);
        var dispatcher = new ActionDispatcher(new ActionRegistry().register(action),
                new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        var evidence = new EvidenceReference("simulation", "SIM-RESULT-7");
        var request = new RecordEpisodeRequest(SCOPE, "Supplier outage", "Capacity was reduced during test",
                EpisodeOutcome.SUCCESS, ActionTestFixtures.NOW, ActionTestFixtures.NOW,
                List.of(new EntityReference("supplier", "SUP-1")),
                List.of(new EpisodeEvent("capacity-reduced", "Supplier capacity changed", ActionTestFixtures.NOW, List.of(evidence))),
                List.of(evidence), ProvenanceSource.SIMULATOR, "SIM-RESULT-7", ActionTestFixtures.NOW, "world-v1");

        var result = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(),
                context(Capability.MEMORY_RECORD_EPISODE, "episode-key"), request);

        assertEquals(ActionStatus.SUCCEEDED, result.status());
        assertEquals(EpisodeStatus.RECORDED, result.value().status());
        assertTrue(result.value().retentionUntil().isAfter(ActionTestFixtures.NOW));
        assertNull(result.value().reviewedBy());
    }

    @Test
    void reasoningActionRejectsEvidenceReferencesNotSuppliedByCaller() {
        var supplied = new EvidenceReference("supplier-event", "EVT-VALID");
        var invented = new EvidenceReference("supplier-event", "EVT-INVENTED");
        ReasoningProvider provider = request -> new ReasoningAssessment("Supplier delay likely", List.of("Supplier reported a delay"),
                List.of(), List.of(), List.of(invented), List.of(), ReasoningConfidence.MEDIUM,
                "The response cites an unsupported identifier.");
        var action = new ReasoningAction(provider);
        var dispatcher = new ActionDispatcher(new ActionRegistry().register(action),
                new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        var request = new ReasoningRequest("Assess supplier status", "The current context reports a delay.", List.of(supplied));

        var result = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(),
                context(Capability.REASONING_INVOKE, null), request);

        assertEquals(ActionStatus.FAILED, result.status());
        assertEquals(ActionFailureCode.INVALID_INPUT, result.failure().code());
    }

    @Test
    void reasoningAssessmentRejectsUnboundedIndividualOutputItems() {
        var oversizedFinding = "x".repeat(1_001);
        assertThrows(IllegalArgumentException.class, () -> new ReasoningAssessment(
                "Assessment", List.of(oversizedFinding), List.of(), List.of(),
                List.of(new EvidenceReference("supplier-event", "EVT-1")), List.of(),
                ReasoningConfidence.MEDIUM, "Bounded rationale"));
    }

    @Test
    void retrievalActionReturnsDeterministicCrossMemoryLookup() {
        var working = new InMemoryWorkingMemoryRepository();
        var episodes = new InMemoryEpisodeRepository();
        var semantic = new InMemorySemanticFactRepository();
        var procedural = new InMemoryProceduralMemoryRepository();
        var retrieval = new MemoryRetrievalService(working, episodes, semantic, procedural);
        var action = new MemoryRetrievalAction(retrieval);
        var dispatcher = new ActionDispatcher(new ActionRegistry().register(action),
                new CapabilityAuthorizer(), new InMemoryActionExecutionLedger());
        var query = new MemoryRetrievalQuery(SCOPE, null, null, null,
                ActionTestFixtures.NOW, ActionTestFixtures.NOW, 10);

        var result = dispatcher.dispatch(action.descriptor().id(), action.inputType(), action.outputType(),
                context(Capability.MEMORY_RETRIEVE, null), query);

        assertEquals(ActionStatus.SUCCEEDED, result.status());
        assertTrue(result.value().workingMemory().isEmpty());
        assertTrue(result.value().verifiedEpisodes().isEmpty());
        assertTrue(result.value().activeSemanticFacts().isEmpty());
        assertTrue(result.value().activeProcedures().isEmpty());
    }

    private static ActionContext context(Capability capability, String key) {
        return ActionContext.create("exec-1", "corr-1",
                new ActionPrincipal("agent", Set.of(CapabilityGrant.all(capability))),
                ActionTestFixtures.NOW, key);
    }
}
