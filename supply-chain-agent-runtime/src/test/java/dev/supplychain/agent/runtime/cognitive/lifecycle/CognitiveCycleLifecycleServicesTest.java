package dev.supplychain.agent.runtime.cognitive.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.supplychain.agent.actions.api.ActionDispatcher;
import dev.supplychain.agent.actions.api.ActionRegistry;
import dev.supplychain.agent.actions.api.CapabilityAuthorizer;
import dev.supplychain.agent.actions.internal.ReasoningAction;
import dev.supplychain.agent.actions.internal.ReasoningAssessment;
import dev.supplychain.agent.actions.internal.ReasoningConfidence;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleServiceOperations;
import dev.supplychain.agent.actions.api.InMemoryActionExecutionLedger;
import dev.supplychain.agent.core.interpretation.InterpretationOutcome;
import dev.supplychain.agent.core.interpretation.RequestInterpretation;
import dev.supplychain.agent.domain.orders.CustomerOrderId;
import dev.supplychain.agent.domain.recovery.RecoveryConstraint;
import dev.supplychain.agent.domain.recovery.RecoveryObjective;
import dev.supplychain.agent.domain.recovery.RecoveryObjectiveId;
import dev.supplychain.agent.domain.recovery.RecoveryObjectiveStatus;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.MemoryConflictException;
import dev.supplychain.agent.memory.common.Provenance;
import dev.supplychain.agent.memory.common.ProvenanceSource;
import dev.supplychain.agent.memory.episodic.Episode;
import dev.supplychain.agent.memory.episodic.EpisodeId;
import dev.supplychain.agent.memory.episodic.EpisodeOutcome;
import dev.supplychain.agent.memory.episodic.EpisodeRepository;
import dev.supplychain.agent.memory.episodic.EpisodicMemoryService;
import dev.supplychain.agent.memory.inmemory.InMemoryEpisodeRepository;
import dev.supplychain.agent.memory.inmemory.InMemoryProceduralMemoryRepository;
import dev.supplychain.agent.memory.inmemory.InMemorySemanticFactRepository;
import dev.supplychain.agent.memory.inmemory.InMemoryWorkingMemoryRepository;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalQuery;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalResult;
import dev.supplychain.agent.memory.retrieval.MemoryRetrievalService;
import dev.supplychain.agent.memory.working.MemoryValue;
import dev.supplychain.agent.memory.working.WorkingMemoryService;
import dev.supplychain.agent.memory.working.WorkingMemorySnapshot;
import dev.supplychain.agent.mechanisms.b1.B1CandidateFeatureProvider;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateFactory;
import dev.supplychain.agent.mechanisms.b1.B1RetrievalSelector;
import dev.supplychain.agent.mechanisms.b1.B1ScoringPolicy;
import dev.supplychain.agent.mechanisms.b1.B1SelectionRequest;
import dev.supplychain.agent.mechanisms.b1.B1SelectionResult;
import dev.supplychain.agent.mechanisms.b1.ExponentialActivationLatencyModel;
import dev.supplychain.agent.mechanisms.b1.RecencyFrequencyActivationEstimator;
import dev.supplychain.agent.mechanisms.b2.DeterministicImpasseDetector;
import dev.supplychain.agent.mechanisms.b2.ImpasseCaseRepository;
import dev.supplychain.agent.mechanisms.b2.ImpasseResolutionService;
import dev.supplychain.agent.mechanisms.b2.InMemoryImpasseCaseRepository;
import dev.supplychain.agent.mechanisms.b2.ParentContextSnapshot;
import dev.supplychain.agent.mechanisms.b2.ResolutionDisposition;
import dev.supplychain.agent.mechanisms.b2.ResolutionFinding;
import dev.supplychain.agent.mechanisms.b2.ImpasseSignal;
import dev.supplychain.agent.mechanisms.b2.ImpasseType;
import dev.supplychain.agent.mechanisms.b2.ImpasseSeverity;
import dev.supplychain.agent.mechanisms.b3.InMemoryWorkspaceRepository;
import dev.supplychain.agent.mechanisms.b3.WorkspaceAdmissionRequest;
import dev.supplychain.agent.mechanisms.b3.WorkspaceAdmissionService;
import dev.supplychain.agent.mechanisms.b3.WorkspaceBroadcastReport;
import dev.supplychain.agent.mechanisms.b3.WorkspaceBroadcastService;
import dev.supplychain.agent.mechanisms.b4.DelegationReturn;
import dev.supplychain.agent.mechanisms.b4.InMemoryRecoveryIntentionRepository;
import dev.supplychain.agent.mechanisms.b4.IntentionStatus;
import dev.supplychain.agent.mechanisms.b4.ObjectiveFingerprint;
import dev.supplychain.agent.mechanisms.b4.ObjectiveSatisfactionProof;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntentionId;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntentionService;
import dev.supplychain.agent.mechanisms.b4.RecoveryMethodReference;
import dev.supplychain.agent.mechanisms.b6.MetaControlAction;
import dev.supplychain.agent.mechanisms.b6.MetaControlDecision;
import dev.supplychain.agent.mechanisms.b6.MetaControlGovernor;
import dev.supplychain.agent.mechanisms.b6.MetaControlPolicy;
import dev.supplychain.agent.mechanisms.b6.ResourceBudget;
import dev.supplychain.agent.mechanisms.b6.ResourceBudgetSnapshot;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment;
import dev.supplychain.agent.mechanisms.b6.UncertaintyDimension;
import dev.supplychain.agent.mechanisms.b6.UncertaintySignal;
import dev.supplychain.agent.planning.api.CandidateEvaluator;
import dev.supplychain.agent.planning.api.PlannerId;
import dev.supplychain.agent.planning.api.PlanEvaluation;
import dev.supplychain.agent.planning.api.PlannerTerminationReason;
import dev.supplychain.agent.planning.api.PlanningAction;
import dev.supplychain.agent.planning.api.PlanningRequest;
import dev.supplychain.agent.planning.api.PlanningResult;
import dev.supplychain.agent.planning.api.PlanningState;
import dev.supplychain.agent.planning.api.PlanningStatistics;
import dev.supplychain.agent.planning.api.PlanningStrategy;
import dev.supplychain.agent.planning.api.PlanningWorldModel;
import dev.supplychain.agent.planning.api.PlanEvaluation;
import dev.supplychain.agent.planning.routing.DefaultPlannerSelectionPolicy;
import dev.supplychain.agent.planning.routing.PlannerRouter;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;
import dev.supplychain.agent.runtime.cognitive.ResourceBudgetExecutionGuard;
import dev.supplychain.agent.runtime.cognitive.decision.CognitiveCycleDecisionPort;
import dev.supplychain.agent.runtime.cognitive.decision.CognitiveCycleDecisionServices;
import dev.supplychain.agent.runtime.cognitive.decision.InMemoryPlanningArtifactStore;
import dev.supplychain.agent.simulator.SupplyChainSimulator;
import dev.supplychain.agent.actions.api.ActionPrincipal;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;

final class CognitiveCycleLifecycleServicesTest {
    private static final Instant NOW = Instant.parse("2026-11-17T22:00:00Z");
    private static final MemoryScopeId SCOPE = new MemoryScopeId("tenant:m6b-test");

    @Test
    void loadContextUsesRealWorkingMemoryAndCreatesPersistentB4Intention() {
        Fixture fixture = new Fixture();
        CycleContext resultContext = context("", 0, "", 0, "");

        ContextLoaded loaded = fixture.lifecycle.loadContext(resultContext);

        var memory = fixture.workingRepository.findById(
                new dev.supplychain.agent.memory.working.WorkingMemoryId(loaded.workingMemoryId())).orElseThrow();
        var intention = fixture.intentionRepository.findById(new RecoveryIntentionId(loaded.intentionId())).orElseThrow();
        assertEquals(SCOPE, memory.scopeId());
        assertTrue(memory.values().containsKey("request:current"));
        assertEquals(IntentionStatus.ACTIVE, intention.status());
        assertEquals(1L, loaded.workingMemoryVersion());
        assertEquals(0L, loaded.intentionVersion());
        ContextLoaded retried = fixture.lifecycle.loadContext(resultContext);
        assertEquals(loaded.workingMemoryId(), retried.workingMemoryId());
        assertEquals(loaded.intentionId(), retried.intentionId());
        assertEquals(loaded.workingMemoryVersion(), retried.workingMemoryVersion());
    }

    @Test
    void observationWritesAreVersionedAndIdempotentAfterReload() {
        Fixture fixture = new Fixture();
        ContextLoaded loaded = fixture.lifecycle.loadContext(context("", 0, "", 0, ""));
        ExternalObservation observation = new ExternalObservation(
                ObservationDisposition.RECONSIDER, "observation:cycle-1", "evidence:cycle-1", "supplier lead time changed");
        CycleContext stale = context(loaded.workingMemoryId(), loaded.workingMemoryVersion(),
                loaded.intentionId(), loaded.intentionVersion(), observation.observationId());

        WorkingMemoryUpdated first = fixture.lifecycle.updateWorkingMemoryFromObservation(stale, observation);
        assertEquals(WorkingMemoryUpdateDisposition.UPDATED, first.disposition());
        assertEquals(2L, first.workingMemoryVersion());

        WorkingMemoryUpdated conflict = fixture.lifecycle.updateWorkingMemoryFromStoredObservation(stale);
        assertEquals(WorkingMemoryUpdateDisposition.CONFLICT, conflict.disposition());
        assertEquals(2L, conflict.workingMemoryVersion());

        CycleContext refreshed = context(first.workingMemoryId(), first.workingMemoryVersion(),
                loaded.intentionId(), loaded.intentionVersion(), observation.observationId());
        WorkingMemoryUpdated retry = fixture.lifecycle.updateWorkingMemoryFromStoredObservation(refreshed);
        assertEquals(WorkingMemoryUpdateDisposition.UPDATED, retry.disposition());
        assertEquals(first.workingMemoryVersion(), retry.workingMemoryVersion());
    }

    @Test
    void objectiveCompletionRequiresVerifiedProofAndOutcomeEpisodeIsIdempotent() {
        Fixture fixture = new Fixture();
        ContextLoaded loaded = fixture.lifecycle.loadContext(context("", 0, "", 0, ""));
        ExternalObservation observation = new ExternalObservation(
                ObservationDisposition.OBJECTIVE_SATISFIED, "observation:complete-1", "evidence:verified-1", "all order lines verified fulfilled");
        CycleContext initial = context(loaded.workingMemoryId(), loaded.workingMemoryVersion(),
                loaded.intentionId(), loaded.intentionVersion(), observation.observationId());
        WorkingMemoryUpdated memory = fixture.lifecycle.updateWorkingMemoryFromObservation(initial, observation);
        CycleContext afterObservation = context(memory.workingMemoryId(), memory.workingMemoryVersion(),
                loaded.intentionId(), loaded.intentionVersion(), observation.observationId());

        EpisodeRecorded first = fixture.lifecycle.recordOutcomeEpisode(afterObservation);
        EpisodeRecorded retry = fixture.lifecycle.recordOutcomeEpisode(afterObservation);

        assertEquals(first.episodeId(), retry.episodeId());
        assertEquals(EpisodeVerificationStatus.RECORDED_UNVERIFIED, first.verificationStatus());
        var intention = fixture.intentionRepository.findById(new RecoveryIntentionId(loaded.intentionId())).orElseThrow();
        assertEquals(IntentionStatus.COMPLETED, intention.status());
        assertNotNull(intention.completionProof());
        var episode = fixture.episodeRepository.findById(new dev.supplychain.agent.memory.episodic.EpisodeId(first.episodeId())).orElseThrow();
        assertEquals(EpisodeOutcome.SUCCESS, episode.outcome());
        assertTrue(episode.evidence().contains(new EvidenceReference("observation", observation.observationId())));
    }

    @Test
    void episodeIdempotencyReturnsTheSameOutcomeButRejectsConflictingReuse() {
        var repository = new InMemoryEpisodeRepository();
        var key = new EvidenceReference("observation", "observation:idempotent-episode");
        var provenance = new Provenance(ProvenanceSource.SIMULATOR, "world:1", NOW.minusSeconds(30), NOW, "fixture-v1");
        Episode first = Episode.recorded(EpisodeId.random(), SCOPE, "Recovery", "Order was recovered",
                EpisodeOutcome.SUCCESS, NOW.minusSeconds(60), NOW.minusSeconds(10), NOW, NOW.plusSeconds(600),
                List.of(), List.of(), List.of(key), provenance);
        Episode replay = Episode.recorded(EpisodeId.random(), SCOPE, "Recovery", "Order was recovered",
                EpisodeOutcome.SUCCESS, NOW.minusSeconds(60), NOW.minusSeconds(10), NOW.plusSeconds(1), NOW.plusSeconds(601),
                List.of(), List.of(), List.of(key), new Provenance(ProvenanceSource.SIMULATOR,
                        "world:1", NOW.minusSeconds(30), NOW.plusSeconds(1), "fixture-v1"));

        Episode stored = repository.appendIdempotent(first, key);
        Episode replayed = repository.appendIdempotent(replay, key);
        assertEquals(stored.id(), replayed.id());

        Episode conflicting = Episode.recorded(EpisodeId.random(), SCOPE, "Recovery", "Different outcome under same observation",
                EpisodeOutcome.FAILURE, NOW.minusSeconds(60), NOW.minusSeconds(10), NOW.plusSeconds(2), NOW.plusSeconds(602),
                List.of(), List.of(), List.of(key), new Provenance(ProvenanceSource.SIMULATOR,
                        "world:1", NOW.minusSeconds(30), NOW.plusSeconds(2), "fixture-v1"));
        assertThrows(MemoryConflictException.class, () -> repository.appendIdempotent(conflicting, key));
    }

    @Test
    void b4MethodChangeCannotAlterTheProtectedObjective() {
        Fixture fixture = new Fixture();
        ContextLoaded loaded = fixture.lifecycle.loadContext(context("", 0, "", 0, ""));
        CycleContext initial = context(loaded.workingMemoryId(), loaded.workingMemoryVersion(),
                loaded.intentionId(), loaded.intentionVersion(), "");
        fixture.domain.nextIntentionDirective = new CognitiveCycleLifecyclePort.IntentionDirective(
                IntentionAction.RECONSIDER, "existing method is no longer feasible", null, "", "");
        IntentionGovernance reconsidered = fixture.lifecycle.governIntention(initial);
        assertEquals(IntentionAction.RECONSIDER, reconsidered.action());

        fixture.domain.nextIntentionDirective = new CognitiveCycleLifecyclePort.IntentionDirective(
                IntentionAction.CHANGE_METHOD, "switch to qualified supplier", new RecoveryMethodReference("supplier-alternative", "candidate:new"),
                "0".repeat(64), "");
        CycleContext pending = context(loaded.workingMemoryId(), loaded.workingMemoryVersion(),
                loaded.intentionId(), reconsidered.intentionVersion(), "");
        assertThrows(SecurityException.class, () -> fixture.lifecycle.governIntention(pending));

        var unchanged = fixture.intentionRepository.findById(new RecoveryIntentionId(loaded.intentionId())).orElseThrow();
        assertEquals(IntentionStatus.RECONSIDERATION_PENDING, unchanged.status());
        assertEquals("method-initial", unchanged.currentMethod().methodId());
    }

    @Test
    void b4LifecycleRejectsMethodReplacementBeforeExplicitReconsideration() {
        Fixture fixture = new Fixture();
        ContextLoaded loaded = fixture.lifecycle.loadContext(context("", 0, "", 0, ""));
        CycleContext active = context(loaded.workingMemoryId(), loaded.workingMemoryVersion(),
                loaded.intentionId(), loaded.intentionVersion(), "");
        fixture.domain.nextIntentionDirective = new CognitiveCycleLifecyclePort.IntentionDirective(
                IntentionAction.CHANGE_METHOD, "replace the method without a reconsideration transition",
                new RecoveryMethodReference("supplier-alternative", "candidate:new"),
                fixture.domain.objectiveFingerprint(active), "");

        assertThrows(IllegalStateException.class, () -> fixture.lifecycle.governIntention(active));

        var unchanged = fixture.intentionRepository.findById(new RecoveryIntentionId(loaded.intentionId())).orElseThrow();
        assertEquals(IntentionStatus.ACTIVE, unchanged.status());
        assertEquals("method-initial", unchanged.currentMethod().methodId());
        assertEquals(loaded.intentionVersion(), unchanged.version());
    }

    @Test
    void b4LifecycleAcceptsMethodChangeOnlyAfterExplicitReconsiderationAndPreservesObjective() {
        Fixture fixture = new Fixture();
        ContextLoaded loaded = fixture.lifecycle.loadContext(context("", 0, "", 0, ""));
        CycleContext active = context(loaded.workingMemoryId(), loaded.workingMemoryVersion(),
                loaded.intentionId(), loaded.intentionVersion(), "");
        var original = fixture.intentionRepository.findById(new RecoveryIntentionId(loaded.intentionId())).orElseThrow();
        String objectiveFingerprint = original.objectiveFingerprint();

        fixture.domain.nextIntentionDirective = new CognitiveCycleLifecyclePort.IntentionDirective(
                IntentionAction.RECONSIDER, "authoritative supplier-capacity evidence invalidated the current method", null, "", "");
        IntentionGovernance reconsidered = fixture.lifecycle.governIntention(active);
        assertEquals(IntentionAction.RECONSIDER, reconsidered.action());

        fixture.domain.nextIntentionDirective = new CognitiveCycleLifecyclePort.IntentionDirective(
                IntentionAction.CHANGE_METHOD, "select an alternative registered recovery method",
                new RecoveryMethodReference("supplier-alternative", "candidate:verified-alternative"),
                objectiveFingerprint, "");
        CycleContext pending = context(loaded.workingMemoryId(), loaded.workingMemoryVersion(),
                loaded.intentionId(), reconsidered.intentionVersion(), "");
        IntentionGovernance changed = fixture.lifecycle.governIntention(pending);

        var accepted = fixture.intentionRepository.findById(new RecoveryIntentionId(loaded.intentionId())).orElseThrow();
        assertEquals(IntentionAction.CHANGE_METHOD, changed.action());
        assertEquals(IntentionStatus.ACTIVE, accepted.status());
        assertEquals("supplier-alternative", accepted.currentMethod().methodId());
        assertEquals("candidate:verified-alternative", accepted.currentMethod().candidatePlanReference());
        assertEquals(objectiveFingerprint, accepted.objectiveFingerprint());
        assertEquals(original.objectiveId(), accepted.objectiveId());
        assertEquals(original.protectedConstraints(), accepted.protectedConstraints());
        assertEquals(reconsidered.intentionVersion() + 1, accepted.version());
    }

    @Test
    void b4LifecycleRejectsStaleIntentionBeforeConsultingTheDirectivePort() {
        Fixture fixture = new Fixture();
        ContextLoaded loaded = fixture.lifecycle.loadContext(context("", 0, "", 0, ""));
        CycleContext stale = context(loaded.workingMemoryId(), loaded.workingMemoryVersion(),
                loaded.intentionId(), loaded.intentionVersion(), "");
        new RecoveryIntentionService(fixture.intentionRepository).requestReconsideration(
                new RecoveryIntentionId(loaded.intentionId()), loaded.intentionVersion(),
                "concurrent authoritative update", NOW.plusSeconds(1));
        fixture.domain.nextIntentionDirective = new CognitiveCycleLifecyclePort.IntentionDirective(
                IntentionAction.CONTINUE, "stale graph must not continue", null, "", "");

        assertThrows(IllegalStateException.class, () -> fixture.lifecycle.governIntention(stale));

        assertEquals(0, fixture.domain.intentionDirectiveCalls,
                "stale graph state must be rejected before a new host directive is requested");
        var latest = fixture.intentionRepository.findById(new RecoveryIntentionId(loaded.intentionId())).orElseThrow();
        assertEquals(IntentionStatus.RECONSIDERATION_PENDING, latest.status());
        assertEquals(loaded.intentionVersion() + 1, latest.version());
    }

    @Test
    void b4AuthorizedTerminationIsExplicitAndCannotMasqueradeAsObjectiveCompletion() {
        Fixture fixture = new Fixture();
        ContextLoaded loaded = fixture.lifecycle.loadContext(context("", 0, "", 0, ""));
        CycleContext active = context(loaded.workingMemoryId(), loaded.workingMemoryVersion(),
                loaded.intentionId(), loaded.intentionVersion(), "");
        fixture.domain.nextIntentionDirective = new CognitiveCycleLifecyclePort.IntentionDirective(
                IntentionAction.TERMINATE, "authorized operator explicitly abandons this recovery", null, "", "authorized-operator");

        IntentionGovernance terminated = fixture.lifecycle.governIntention(active);

        var finalIntention = fixture.intentionRepository.findById(new RecoveryIntentionId(loaded.intentionId())).orElseThrow();
        assertEquals(IntentionAction.TERMINATE, terminated.action());
        assertEquals(IntentionStatus.TERMINATED, finalIntention.status());
        assertEquals(loaded.intentionVersion() + 1, finalIntention.version());
        assertEquals(ObjectiveFingerprint.from(fixture.domain.objective).value(), finalIntention.objectiveFingerprint());
        assertNull(finalIntention.completionProof(), "termination must not be represented as verified objective completion");
        assertThrows(IllegalStateException.class, () -> fixture.lifecycle.governIntention(active));
    }

    @Test
    void b4TerminationRequiresAnAuthenticatedAuthorizedActor() {
        Fixture fixture = new Fixture();
        ContextLoaded loaded = fixture.lifecycle.loadContext(context("", 0, "", 0, ""));
        CycleContext active = context(loaded.workingMemoryId(), loaded.workingMemoryVersion(),
                loaded.intentionId(), loaded.intentionVersion(), "");
        fixture.domain.nextIntentionDirective = new CognitiveCycleLifecyclePort.IntentionDirective(IntentionAction.TERMINATE,
                "operator requested termination", null, "", "spoofed-operator");

        assertThrows(SecurityException.class, () -> fixture.lifecycle.governIntention(active));
        var intention = fixture.intentionRepository.findById(new RecoveryIntentionId(loaded.intentionId())).orElseThrow();
        assertEquals(IntentionStatus.ACTIVE, intention.status());
    }

    @Test
    void b6DecisionIsPersistedAndResourceExhaustionStopsTheCycle() {
        Fixture fixture = new Fixture();
        CycleContext context = context("", 0, "", 0, "");
        fixture.domain.metaControl = new CognitiveCycleLifecyclePort.MetaControlInputs(
                new ResourceBudgetSnapshot("execution:m6b",
                        new ResourceBudget(Map.of(dev.supplychain.agent.mechanisms.b6.ResourceUnit.MODEL_CALLS, 0L), NOW.plusSeconds(60)),
                        Map.of(dev.supplychain.agent.mechanisms.b6.ResourceUnit.MODEL_CALLS, 1L), Map.of(), NOW),
                uncertainty(0.1), ResourceUsage.of(dev.supplychain.agent.mechanisms.b6.ResourceUnit.MODEL_CALLS, 1));

        var decision = fixture.lifecycle.governMetaControl(context);

        assertEquals(MetaControlAction.STOP, decision.action());
        assertEquals(1, fixture.domain.metaControlRecords);
        assertNotNull(fixture.domain.lastMetaControlDecision);
    }

    @Test
    void b6GuardRejectsAnUnbudgetedRegisteredReasoningAction() {
        var ledger = new dev.supplychain.agent.mechanisms.b6.InMemoryResourceBudgetLedger();
        var budget = new ResourceBudget(Map.of(dev.supplychain.agent.mechanisms.b6.ResourceUnit.MODEL_CALLS, 3L),
                NOW.plusSeconds(60));
        var guard = new ResourceBudgetExecutionGuard(ledger, ignored -> budget, Clock.fixed(NOW, ZoneOffset.UTC));
        var unbudgeted = new ReasoningAction(request -> new ReasoningAssessment("Supplier assessment", List.of(),
                List.of(), List.of(), List.of(), List.of(), ReasoningConfidence.UNASSESSED, "No evidence-backed conclusion"));
        var registry = new ActionRegistry().register(unbudgeted);

        assertThrows(SecurityException.class, () -> new Fixture(guard, guard, registry));
    }

    @Test
    void lateUnbudgetedReasoningRegistrationIsRejectedAtAuthorizationBoundary() {
        var ledger = new dev.supplychain.agent.mechanisms.b6.InMemoryResourceBudgetLedger();
        var budget = new ResourceBudget(Map.of(dev.supplychain.agent.mechanisms.b6.ResourceUnit.MODEL_CALLS, 3L),
                NOW.plusSeconds(60));
        var guard = new ResourceBudgetExecutionGuard(ledger, ignored -> budget, Clock.fixed(NOW, ZoneOffset.UTC));
        var registry = new ActionRegistry();
        Fixture fixture = new Fixture(guard, guard, registry);
        registry.register(new ReasoningAction(request -> new ReasoningAssessment("Unbudgeted assessment", List.of(),
                List.of(), List.of(), List.of(), List.of(), ReasoningConfidence.UNASSESSED,
                "No trusted execution budget was bound")));

        assertThrows(SecurityException.class,
                () -> fixture.lifecycle.prepareAuthorization(context("", 0, "", 0, "")));
    }

    @Test
    void completeCompositionRejectsDifferentB6GuardInstancesForM5AndLifecycle() {
        var ledger = new dev.supplychain.agent.mechanisms.b6.InMemoryResourceBudgetLedger();
        var budget = new ResourceBudget(Map.of(dev.supplychain.agent.mechanisms.b6.ResourceUnit.MODEL_CALLS, 3L),
                NOW.plusSeconds(60));
        var decisionGuard = new ResourceBudgetExecutionGuard(ledger, ignored -> budget, Clock.fixed(NOW, ZoneOffset.UTC));
        var lifecycleGuard = new ResourceBudgetExecutionGuard(ledger, ignored -> budget, Clock.fixed(NOW, ZoneOffset.UTC));
        var fixture = new Fixture(decisionGuard, lifecycleGuard, new ActionRegistry());

        assertThrows(IllegalArgumentException.class, () -> new CognitiveCycleServiceOperations(
                fixture.lifecycle.decisionServices(), fixture.lifecycle));
    }

    @Test
    void b6MetaControlUsesTheSharedDurableLedgerSnapshotRatherThanHostSuppliedCounters() {
        var ledger = new dev.supplychain.agent.mechanisms.b6.InMemoryResourceBudgetLedger();
        var budget = new ResourceBudget(Map.of(dev.supplychain.agent.mechanisms.b6.ResourceUnit.MODEL_CALLS, 2L),
                NOW.plusSeconds(60));
        ledger.register("execution:m6b", budget);
        ledger.reserve("execution:m6b", "prior-model-work",
                ResourceUsage.of(dev.supplychain.agent.mechanisms.b6.ResourceUnit.MODEL_CALLS, 2L), NOW);
        ledger.commit("execution:m6b", "prior-model-work",
                ResourceUsage.of(dev.supplychain.agent.mechanisms.b6.ResourceUnit.MODEL_CALLS, 2L), NOW);
        var guard = new ResourceBudgetExecutionGuard(ledger, ignored -> budget, Clock.fixed(NOW, ZoneOffset.UTC));
        Fixture fixture = new Fixture(guard);
        // Deliberately stale host counters: the durable ledger is authoritative when B6 is wired.
        fixture.domain.metaControl = new CognitiveCycleLifecyclePort.MetaControlInputs(
                new ResourceBudgetSnapshot("execution:m6b", budget, Map.of(), Map.of(), NOW),
                uncertainty(0.1), ResourceUsage.of(dev.supplychain.agent.mechanisms.b6.ResourceUnit.MODEL_CALLS, 1));

        var decision = fixture.lifecycle.governMetaControl(context("", 0, "", 0, ""));

        assertEquals(MetaControlAction.INTERRUPT_FOR_HUMAN, decision.action());
        assertEquals(2L, fixture.domain.lastMetaControlInputs.budget().consumed()
                .get(dev.supplychain.agent.mechanisms.b6.ResourceUnit.MODEL_CALLS));
    }

    @Test
    void b2ResolutionRunsOnlyDeclaredChildCapabilitiesAndReturnsToParentIntention() {
        Fixture fixture = new Fixture();
        ContextLoaded loaded = fixture.lifecycle.loadContext(context("", 0, "", 0, ""));
        CycleContext initial = context(loaded.workingMemoryId(), loaded.workingMemoryVersion(),
                loaded.intentionId(), loaded.intentionVersion(), "");
        fixture.domain.impasseRequest = new CognitiveCycleLifecyclePort.ImpasseOpenRequest(
                "impasse:m6b", new ImpasseSignal(ImpasseType.MISSING_CONFIRMATION, ImpasseSeverity.HIGH,
                "supplier:s-1", "supplier confirmation unavailable", List.of(new EvidenceReference("supplier", "s-1")), NOW),
                Set.of(dev.supplychain.agent.actions.api.Capability.SUPPLIER_READ),
                Set.of(dev.supplychain.agent.actions.api.Capability.SUPPLIER_READ),
                NOW.plusSeconds(120), 2);
        fixture.domain.childPlan = new CognitiveCycleLifecyclePort.ChildInvestigationPlan(
                List.of(new CognitiveCycleLifecyclePort.ChildInvestigationStep("read-supplier", 
                        dev.supplychain.agent.actions.api.Capability.SUPPLIER_READ, "read current supplier status")),
                ResolutionDisposition.EVIDENCE_CONFIRMED, List.of(), DelegationReturn.NO_METHOD_CHANGE);
        fixture.domain.findings = List.of(new ResolutionFinding("Supplier is active but has not confirmed the order",
                List.of(new EvidenceReference("supplier-status", "s-1:v3"))));

        ImpasseOpened opened = fixture.lifecycle.openImpasse(initial);
        CycleContext withImpasse = new CycleContext(initial.requestId(), initial.executionId(), initial.memoryScopeId(),
                initial.request(), initial.normalizedIntent(), initial.summary(), initial.clarificationContext(),
                initial.clarificationRound(), initial.workingMemoryId(), initial.workingMemoryVersion(), initial.evidenceSetId(),
                initial.evidenceRequestCount(), initial.evidenceResponseId(), initial.workspaceSnapshotId(), initial.intentionId(),
                loaded.intentionVersion() + 1, initial.plannerId(), initial.plannerSessionId(), initial.candidateSetId(),
                initial.planningRound(), initial.selectedCandidateId(), opened.caseId(), "", initial.approvalId(),
                initial.approvalResponseId(), initial.approverId(), initial.humanControlResponseId(), initial.humanControlActorId(),
                initial.executionOperationId(), initial.observationId(), initial.lastReason());

        ImpasseInvestigated investigated = fixture.lifecycle.investigateImpasse(withImpasse);
        ImpasseResolved resolved = fixture.lifecycle.validateImpasseReturn(withImpasse);

        assertTrue(investigated.investigationReference().contains("b2-child-result"));
        assertEquals(ImpasseDisposition.RESOLVED, resolved.disposition());
        var intention = fixture.intentionRepository.findById(new RecoveryIntentionId(loaded.intentionId())).orElseThrow();
        assertEquals(IntentionStatus.ACTIVE, intention.status());
        assertEquals(1, fixture.domain.executedChildSteps);
    }

    @Test
    void b2ChildCannotExecuteACapabilityOutsideItsDelegatedScope() {
        Fixture fixture = new Fixture();
        ContextLoaded loaded = fixture.lifecycle.loadContext(context("", 0, "", 0, ""));
        CycleContext initial = context(loaded.workingMemoryId(), loaded.workingMemoryVersion(),
                loaded.intentionId(), loaded.intentionVersion(), "");
        fixture.domain.impasseRequest = new CognitiveCycleLifecyclePort.ImpasseOpenRequest(
                "impasse:unauthorized-child", new ImpasseSignal(ImpasseType.MISSING_CONFIRMATION,
                ImpasseSeverity.HIGH, "supplier:s-1", "supplier confirmation unavailable", List.of(), NOW),
                Set.of(dev.supplychain.agent.actions.api.Capability.SUPPLIER_READ),
                Set.of(dev.supplychain.agent.actions.api.Capability.SUPPLIER_READ), NOW.plusSeconds(120), 2);
        fixture.domain.childPlan = new CognitiveCycleLifecyclePort.ChildInvestigationPlan(
                List.of(new CognitiveCycleLifecyclePort.ChildInvestigationStep("reserve-stock",
                        dev.supplychain.agent.actions.api.Capability.INVENTORY_RESERVE, "attempt inventory reservation")),
                ResolutionDisposition.UNRESOLVED, List.of("attempt exceeds child authority"), DelegationReturn.NO_METHOD_CHANGE);

        ImpasseOpened opened = fixture.lifecycle.openImpasse(initial);
        CycleContext delegated = new CycleContext(initial.requestId(), initial.executionId(), initial.memoryScopeId(),
                initial.request(), initial.normalizedIntent(), initial.summary(), initial.clarificationContext(),
                initial.clarificationRound(), initial.workingMemoryId(), initial.workingMemoryVersion(), initial.evidenceSetId(),
                initial.evidenceRequestCount(), initial.evidenceResponseId(), initial.workspaceSnapshotId(), initial.intentionId(),
                loaded.intentionVersion() + 1, initial.plannerId(), initial.plannerSessionId(), initial.candidateSetId(),
                initial.planningRound(), initial.selectedCandidateId(), opened.caseId(), "", initial.approvalId(),
                initial.approvalResponseId(), initial.approverId(), initial.humanControlResponseId(), initial.humanControlActorId(),
                initial.executionOperationId(), initial.observationId(), initial.lastReason());

        assertThrows(SecurityException.class, () -> fixture.lifecycle.investigateImpasse(delegated));
        assertEquals(0, fixture.domain.executedChildSteps);
    }

    private static UncertaintyAssessment uncertainty(double value) {
        return new UncertaintyAssessment(Map.of(
                UncertaintyDimension.EVIDENCE, new UncertaintySignal(UncertaintyDimension.EVIDENCE, value, "test evidence", 1),
                UncertaintyDimension.HYPOTHESIS, new UncertaintySignal(UncertaintyDimension.HYPOTHESIS, value, "test hypothesis", 1),
                UncertaintyDimension.PLAN, new UncertaintySignal(UncertaintyDimension.PLAN, value, "test plan", 1)));
    }

    private static CycleContext context(String memoryId, long memoryVersion, String intentionId,
                                        long intentionVersion, String observationId) {
        return new CycleContext("request:m6b", "execution:m6b", SCOPE.value(), "Recover affected customer order",
                "recover customer order", "test context", "", 0, memoryId, memoryVersion,
                "evidence-set:m6b", 0, "", "workspace:m6b", intentionId, intentionVersion,
                "", "", "", 0, "", "", "", "", "", "", "", "", "action-batch:m6b", observationId, "");
    }

    private static final class Fixture {
        private final InMemoryWorkingMemoryRepository workingRepository = new InMemoryWorkingMemoryRepository();
        private final InMemoryRecoveryIntentionRepository intentionRepository = new InMemoryRecoveryIntentionRepository();
        private final InMemoryEpisodeRepository episodeRepository = new InMemoryEpisodeRepository();
        private final TestDomainPort domain = new TestDomainPort();
        private final CognitiveCycleLifecycleServices lifecycle;

        private Fixture() { this(null, null, new ActionRegistry()); }

        private Fixture(ResourceBudgetExecutionGuard budgetGuard) {
            this(budgetGuard, budgetGuard, new ActionRegistry());
        }

        private Fixture(ResourceBudgetExecutionGuard decisionGuard,
                        ResourceBudgetExecutionGuard lifecycleGuard,
                        ActionRegistry registry) {
            var scope = SCOPE;
            var working = new WorkingMemoryService(workingRepository, Duration.ofDays(1));
            var episodic = new EpisodicMemoryService(episodeRepository, Duration.ofDays(7));
            var decisions = noOpDecisionServices(decisionGuard);
            dev.supplychain.agent.core.interpretation.RequestInterpreter interpreter = request -> new RequestInterpretation(InterpretationOutcome.READY,
                    request.originalText(), "deterministic test interpretation");
            lifecycle = new CognitiveCycleLifecycleServices(working, episodic,
                    new ImpasseResolutionService(new DeterministicImpasseDetector(), new InMemoryImpasseCaseRepository()),
                    new RecoveryIntentionService(intentionRepository), new MetaControlGovernor(new MetaControlPolicy(0.4, 0.8, 0.2)),
                    new ActionDispatcher(registry, new CapabilityAuthorizer(), new InMemoryActionExecutionLedger()), registry,
                    interpreter, decisions, domain, new TestAuthority(), new InMemoryAuthorizedActionPlanStore(),
                    new InMemoryImpasseChildArtifactStore(), new InMemoryActionExecutionBatchStore(),
                    new InMemoryObservationStore(), null, lifecycleGuard, Clock.fixed(NOW, ZoneOffset.UTC));
            domain.objective = objective();
        }
    }

    private static RecoveryObjective objective() {
        return new RecoveryObjective(new RecoveryObjectiveId("objective:m6b"), "Restore customer order fulfillment",
                Set.of(new CustomerOrderId("order:m6b")), NOW.plus(Duration.ofDays(2)),
                Set.of(RecoveryConstraint.NO_OVER_ALLOCATION, RecoveryConstraint.APPROVAL_REQUIRED_FOR_EXTERNAL_COMMITMENT),
                RecoveryObjectiveStatus.ACTIVE, 0);
    }

    private static CognitiveCycleDecisionServices noOpDecisionServices() {
        return noOpDecisionServices(null);
    }

    private static CognitiveCycleDecisionServices noOpDecisionServices(ResourceBudgetExecutionGuard budgetGuard) {
        var working = new InMemoryWorkingMemoryRepository();
        var retrieval = new MemoryRetrievalService(working, new InMemoryEpisodeRepository(),
                new InMemorySemanticFactRepository(), new InMemoryProceduralMemoryRepository());
        List<PlanningStrategy> strategies = List.of(PlannerId.COT_SEQUENTIAL, PlannerId.TOT_BOUNDED_SEARCH, PlannerId.RAP_INSPIRED_MCTS)
                .stream().<PlanningStrategy>map(id -> new PlanningStrategy() {
                    @Override public PlannerId id() { return id; }
                    @Override public PlanningResult plan(PlanningRequest request) {
                        return new PlanningResult(id, List.of(), PlannerTerminationReason.NO_AVAILABLE_ACTIONS,
                                new PlanningStatistics(0, 0, 0, 0, 0, 0, Duration.ZERO), "unused-session",
                                "not used by lifecycle tests", List.of());
                    }
                }).toList();
        PlanningWorldModel world = new PlanningWorldModel() {
            @Override public String configurationFingerprint() { return "unused-world"; }
            @Override public PlanningState initialState(PlanningRequest request) { throw new UnsupportedOperationException(); }
            @Override public List<PlanningAction> availableActions(PlanningRequest request, PlanningState state) { return List.of(); }
            @Override public dev.supplychain.agent.planning.api.WorldTransition apply(PlanningRequest request, PlanningState state,
                                                                                   PlanningAction action) { throw new UnsupportedOperationException(); }
        };
        CandidateEvaluator evaluator = new CandidateEvaluator() {
            @Override public String configurationFingerprint() { return "unused-evaluator"; }
            @Override public PlanEvaluation evaluate(PlanningRequest request, PlanningState state) { throw new UnsupportedOperationException(); }
        };
        return new CognitiveCycleDecisionServices(retrieval, new B1MemoryCandidateFactory(),
                new B1RetrievalSelector(new RecencyFrequencyActivationEstimator(0.5, 1.0),
                        new ExponentialActivationLatencyModel(100, 0.5, 1, 10_000)),
                new WorkspaceAdmissionService(new InMemoryWorkspaceRepository()),
                new WorkspaceBroadcastService(Map.of()), new PlannerRouter(strategies, new DefaultPlannerSelectionPolicy()),
                world, evaluator, new InMemoryPlanningArtifactStore(), new NoopDecisionPort(),
                Clock.fixed(NOW, ZoneOffset.UTC), budgetGuard);
    }

    private static final class NoopDecisionPort implements CognitiveCycleDecisionPort {
        @Override public MemoryScopeId memoryScope(CycleContext context) { return SCOPE; }
        @Override public MemoryRetrievalQuery retrievalQuery(CycleContext context, Instant at) { throw new UnsupportedOperationException(); }
        @Override public B1SelectionRequest b1SelectionRequest(CycleContext context, Instant at) { throw new UnsupportedOperationException(); }
        @Override public B1CandidateFeatureProvider b1FeatureProvider(CycleContext context) { throw new UnsupportedOperationException(); }
        @Override public EvidenceRetrieved persistEvidenceSelection(CycleContext context, MemoryRetrievalResult retrieved,
                                                                     B1SelectionResult selected, Instant at) { throw new UnsupportedOperationException(); }
        @Override public WorkspaceAdmissionRequest workspaceAdmissionRequest(CycleContext context, Instant at) { throw new UnsupportedOperationException(); }
        @Override public Set<String> workspaceConsumers(CycleContext context) { throw new UnsupportedOperationException(); }
        @Override public void recordWorkspaceBroadcast(CycleContext context, WorkspaceBroadcastReport report, Instant at) { throw new UnsupportedOperationException(); }
        @Override public PlanningRequest planningRequest(CycleContext context, Instant at) { throw new UnsupportedOperationException(); }
    }

    private static final class TestDomainPort implements CognitiveCycleLifecyclePort {
        private RecoveryObjective objective;
        private IntentionDirective nextIntentionDirective = new IntentionDirective(IntentionAction.CONTINUE, "continue", null, "", "");
        private MetaControlInputs metaControl = new MetaControlInputs(
                new ResourceBudgetSnapshot("execution:m6b", new ResourceBudget(Map.of(), NOW.plusSeconds(60)), Map.of(), Map.of(), NOW),
                uncertainty(0.1), new ResourceUsage(Map.of()));
        private MetaControlDecision lastMetaControlDecision;
        private MetaControlInputs lastMetaControlInputs;
        private int metaControlRecords;
        private ImpasseOpenRequest impasseRequest;
        private ChildInvestigationPlan childPlan;
        private List<ResolutionFinding> findings = List.of();
        private int executedChildSteps;
        private int intentionDirectiveCalls;

        @Override public MemoryScopeId memoryScope(CycleContext context) { return new MemoryScopeId(context.memoryScopeId()); }
        @Override public dev.supplychain.agent.memory.working.WorkingMemoryId workingMemoryId(CycleContext context) {
            return new dev.supplychain.agent.memory.working.WorkingMemoryId("working-memory:" + context.executionId());
        }
        @Override public dev.supplychain.agent.core.interpretation.InterpretationRequest interpretationRequest(CycleContext context, Instant at) {
            return new dev.supplychain.agent.core.interpretation.InterpretationRequest(context.request(), context.clarificationContext(), context.clarificationRound());
        }
        @Override public void persistClarification(CycleContext context, Instant at) { }
        @Override public Optional<IntentionSeed> intentionSeed(CycleContext context, Instant at) {
            return Optional.of(new IntentionSeed(new RecoveryIntentionId("intention:" + context.executionId()), objective,
                    "test-owner", new RecoveryMethodReference("method-initial", "candidate:initial")));
        }
        @Override public List<ContextFact> contextFacts(CycleContext context, WorkingMemorySnapshot current, Instant at) {
            return List.of(new ContextFact("request:current", new MemoryValue.Text(context.request()),
                    List.of(new EvidenceReference("request", context.requestId()))));
        }
        @Override public ExternalEvidenceDecision acceptExternalEvidence(CycleContext context, Instant at) {
            return new ExternalEvidenceDecision(EvidenceAcceptance.REJECTED, "", List.of(), "not required by lifecycle tests");
        }
        @Override public String objectiveFingerprint(CycleContext context) { return ObjectiveFingerprint.from(objective).value(); }
        @Override public ImpasseOpenRequest impasseOpenRequest(CycleContext context, Instant at) {
            return Objects.requireNonNull(impasseRequest, "impasseRequest not configured");
        }
        @Override public ChildInvestigationPlan planChildInvestigation(CycleContext context,
                dev.supplychain.agent.mechanisms.b2.ImpasseResolutionCase resolutionCase, Instant at) {
            return Objects.requireNonNull(childPlan, "childPlan not configured");
        }
        @Override public List<ResolutionFinding> executeChildInvestigationStep(CycleContext context,
                dev.supplychain.agent.mechanisms.b2.ImpasseResolutionCase resolutionCase,
                ChildInvestigationStep step, Instant at) {
            executedChildSteps++;
            return findings;
        }
        @Override public ParentContextSnapshot currentParentContext(CycleContext context, WorkingMemorySnapshot latestWorkingMemory) {
            return new ParentContextSnapshot(latestWorkingMemory.scopeId(), context.requestId(), latestWorkingMemory.version(), objectiveFingerprint(context));
        }
        @Override public IntentionDirective intentionDirective(CycleContext context,
                dev.supplychain.agent.mechanisms.b4.RecoveryIntention current, Instant at) {
            intentionDirectiveCalls++;
            return nextIntentionDirective;
        }
        @Override public MetaControlInputs metaControlInputs(CycleContext context, Instant at) { return metaControl; }
        @Override public void recordMetaControlDecision(CycleContext context, MetaControlInputs inputs,
                dev.supplychain.agent.mechanisms.b6.MetaControlDecision decision, Instant at) {
            metaControlRecords++;
            lastMetaControlInputs = inputs;
            lastMetaControlDecision = decision;
        }
        @Override public PlanningRequest revalidationRequest(CycleContext context,
                dev.supplychain.agent.runtime.cognitive.decision.CognitiveCycleDecisionServices.SelectedPlanningCandidate selected,
                WorkingMemorySnapshot latestWorkingMemory, Instant at) { throw new UnsupportedOperationException(); }
        @Override public List<ActionDispatchCommand<?, ?>> actionCommands(CycleContext context,
                dev.supplychain.agent.runtime.cognitive.decision.CognitiveCycleDecisionServices.SelectedPlanningCandidate selected,
                Instant at) { throw new UnsupportedOperationException(); }
        @Override public ExternalObservation observeAndReconcile(CycleContext context, ActionExecutionBatch batch, Instant at) {
            throw new UnsupportedOperationException();
        }
        @Override public EpisodeDraft outcomeEpisode(CycleContext context, ExternalObservation observation, Instant at) {
            return new EpisodeDraft("Verified recovery", "Customer order fulfillment was verified by the simulator", EpisodeOutcome.SUCCESS,
                    NOW.minusSeconds(60), NOW, List.of(), List.of(),
                    List.of(new EvidenceReference("order", "order:m6b")),
                    new Provenance(ProvenanceSource.SIMULATOR, "supply-chain-simulator", NOW, NOW, "test-v1"));
        }
        @Override public ObjectiveSatisfactionProof objectiveSatisfactionProof(CycleContext context,
                ExternalObservation observation, Instant at) {
            return new ObjectiveSatisfactionProof(objectiveFingerprint(context),
                    List.of(new EvidenceReference("observation", observation.observationId())),
                    "test-domain-verifier", "supply-chain-objective-policy-v1", at);
        }
        @Override public void recordEscalation(CycleContext context, String reason, Instant at) { }
    }

    private static final class TestAuthority implements CognitiveCycleAuthorityPort {
        @Override public AuthorizationPrepared requestApproval(CycleContext context, ApprovalBinding binding,
                List<dev.supplychain.agent.actions.api.ActionDescriptor> actionDescriptors, Instant at) {
            throw new UnsupportedOperationException();
        }
        @Override public ApprovalResolved resolveApproval(CycleContext context, ApprovalBinding binding, Instant at) {
            throw new UnsupportedOperationException();
        }
        @Override public boolean isApprovalStillValid(CycleContext context, ApprovalBinding binding, String approvalId, Instant at) { return false; }
        @Override public HumanControlResolution resolveHumanControl(CycleContext context, Instant at) {
            return new HumanControlResolution(HumanControlAction.CONTINUE, "test-operator", context.humanControlResponseId(), "continue");
        }
        @Override public boolean isTerminationAuthorized(CycleContext context, String actorId, Instant at) { return "authorized-operator".equals(actorId); }
        @Override public ActionPrincipal executionPrincipal(CycleContext context, Instant at) {
            return new ActionPrincipal("test-service", Set.of());
        }
    }
}
